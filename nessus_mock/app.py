"""
CyberAsk Nessus Mock API
========================
Flask server that emulates the Nessus / Tenable REST endpoints used by the
CyberAsk Scanner Android app — for Google Play review without exposing a real
Nessus instance.

Auth header (same as app):
  X-ApiKeys: accessKey=<key>; secretKey=<secret>
"""

from __future__ import annotations

import logging
import re
import threading
import time
from functools import wraps
from typing import Any, Callable

from flask import Flask, Response, g, jsonify, request

from config import load_settings
from store import MockStore

settings = load_settings()
store = MockStore(settings)

logging.basicConfig(
    level=getattr(logging, settings.log_level, logging.INFO),
    format="%(asctime)s %(levelname)s %(message)s",
)
log = logging.getLogger("nessus-mock")

_reseed_thread_started = False
_reseed_thread_lock = threading.Lock()


def _start_reseed_scheduler() -> None:
    """Background wipe+reseed on RESEED_INTERVAL_HOURS (default 6). 0 disables."""
    global _reseed_thread_started
    with _reseed_thread_lock:
        if _reseed_thread_started:
            return
        interval = store.reseed_interval_seconds()
        if interval <= 0:
            log.info("Automatic reseed disabled (RESEED_INTERVAL_HOURS=0)")
            _reseed_thread_started = True
            return

        def _loop() -> None:
            log.info(
                "Automatic reseed enabled every %.2f hours (%.0f seconds)",
                settings.reseed_interval_hours,
                interval,
            )
            while True:
                # Sleep in chunks so interval config changes after restart still apply cleanly
                remaining = store.seconds_until_reseed()
                if remaining is None:
                    return
                # Wake periodically; reseed when due
                sleep_for = min(max(remaining, 1.0), 60.0)
                time.sleep(sleep_for)
                if store.due_for_reseed():
                    try:
                        store.reset_and_reseed()
                    except Exception:
                        log.exception("Automatic reseed failed")

        t = threading.Thread(target=_loop, name="nessus-mock-reseed", daemon=True)
        t.start()
        _reseed_thread_started = True

# Optional path prefixes some proxies / users add (rewritten before routing)
_STRIP_PREFIXES = ("/api/v1", "/rest", "/nessus")


class _PrefixStripMiddleware:
    """Strip optional API prefixes so /api/v1/scans still hits /scans."""

    def __init__(self, wsgi_app):
        self.wsgi_app = wsgi_app

    def __call__(self, environ, start_response):
        path = environ.get("PATH_INFO", "") or ""
        for prefix in _STRIP_PREFIXES:
            if path == prefix or path.startswith(prefix + "/"):
                environ["PATH_INFO"] = path[len(prefix) :] or "/"
                break
        return self.wsgi_app(environ, start_response)


def create_app() -> Flask:
    app = Flask(__name__)
    app.config["JSON_SORT_KEYS"] = False
    app.wsgi_app = _PrefixStripMiddleware(app.wsgi_app)

    def parse_api_keys(header: str | None) -> tuple[str, str]:
        """Parse X-ApiKeys: accessKey=...; secretKey=... (flexible spacing)."""
        if not header:
            return "", ""
        access = ""
        secret = ""
        # accessKey=...
        m = re.search(r"accessKey\s*=\s*([^;]+)", header, re.I)
        if m:
            access = m.group(1).strip().strip('"').strip("'")
        m = re.search(r"secretKey\s*=\s*([^;]+)", header, re.I)
        if m:
            secret = m.group(1).strip().strip('"').strip("'")
        return access, secret

    def require_api_keys(fn: Callable[..., Any]) -> Callable[..., Any]:
        @wraps(fn)
        def wrapper(*args: Any, **kwargs: Any):
            # Server status is callable without keys (app tests connectivity early)
            access, secret = parse_api_keys(request.headers.get("X-ApiKeys"))
            # Also accept Authorization-style fallbacks for manual testing
            if not access:
                access = request.headers.get("X-Access-Key", "")
            if not secret:
                secret = request.headers.get("X-Secret-Key", "")

            ok = access == settings.access_key and secret == settings.secret_key
            if not ok:
                log.warning(
                    "Auth failed from %s path=%s",
                    request.remote_addr,
                    request.path,
                )
                return (
                    jsonify(
                        {
                            "error": "InvalidCredentials",
                            "message": "Invalid accessKey or secretKey. "
                            "Use the demo credentials from the review notes / .env.",
                        }
                    ),
                    401,
                )
            g.access_key = access
            return fn(*args, **kwargs)

        return wrapper

    def empty_ok() -> Response:
        return Response("{}", status=200, mimetype="application/json")

    # ------------------------------------------------------------------ health
    @app.get("/")
    def root():
        remaining = store.seconds_until_reseed()
        last = store.last_reseed_at()
        return jsonify(
            {
                "service": "CyberAsk Nessus Mock",
                "purpose": "Google Play review demo API compatible with CyberAsk Scanner",
                "status": "ok",
                "docs": "See README.md — configure the Android app with this host and demo API keys.",
                "persistence": "sqlite",
                "reseed": {
                    "interval_hours": settings.reseed_interval_hours,
                    "enabled": remaining is not None,
                    "last_reseed_at": last,
                    "seconds_until_reseed": remaining,
                },
                "endpoints": [
                    "GET /server/status",
                    "GET /scans",
                    "GET /scanners",
                    "GET /groups",
                    "GET /editor/scan/templates",
                ],
            }
        )

    @app.get("/server/status")
    def server_status():
        # No auth required so reviewers can probe the host; authenticated calls still work
        return jsonify({"status": "ready", "progress": "100"})

    # ------------------------------------------------------------------- scans
    @app.get("/scans")
    @require_api_keys
    def list_scans():
        return jsonify(store.list_scans())

    @app.post("/scans")
    @require_api_keys
    def create_scan():
        body = request.get_json(silent=True) or {}
        if not body.get("uuid") and not (body.get("settings") or {}).get("name"):
            # still accept minimal bodies for resilience
            pass
        result = store.create_scan(body)
        return jsonify(result), 200

    @app.get("/scans/<int:scan_id>")
    @require_api_keys
    def get_scan(scan_id: int):
        history_id = request.args.get("history_id", type=int)
        detail = store.get_scan(scan_id, history_id)
        if detail is None:
            return jsonify({"error": "NotFound", "message": f"Scan {scan_id} not found"}), 404
        return jsonify(detail)

    @app.get("/scans/<int:scan_id>/hosts/<int:host_id>")
    @require_api_keys
    def get_scan_host(scan_id: int, host_id: int):
        history_id = request.args.get("history_id", type=int)
        if store.get_scan(scan_id) is None:
            return jsonify({"error": "NotFound"}), 404
        return jsonify(store.get_scan_host(scan_id, host_id, history_id))

    @app.post("/scans/<int:scan_id>/launch")
    @require_api_keys
    def launch_scan(scan_id: int):
        if not store.set_scan_status(scan_id, "running"):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    @app.post("/scans/<int:scan_id>/stop")
    @require_api_keys
    def stop_scan(scan_id: int):
        if not store.set_scan_status(scan_id, "canceled"):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    @app.post("/scans/<int:scan_id>/pause")
    @require_api_keys
    def pause_scan(scan_id: int):
        if not store.set_scan_status(scan_id, "paused"):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    @app.post("/scans/<int:scan_id>/resume")
    @require_api_keys
    def resume_scan(scan_id: int):
        if not store.set_scan_status(scan_id, "running"):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    @app.delete("/scans/<int:scan_id>")
    @require_api_keys
    def delete_scan(scan_id: int):
        if not store.delete_scan(scan_id):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    @app.put("/scans/<int:scan_id>")
    @require_api_keys
    def update_scan(scan_id: int):
        body = request.get_json(silent=True) or {}
        settings_body = body.get("settings") or {}
        name = settings_body.get("name")
        if not name:
            return jsonify({"error": "BadRequest", "message": "settings.name required"}), 400
        if not store.rename_scan(scan_id, name):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    # ----------------------------------------------------------------- export
    @app.post("/scans/<int:scan_id>/export")
    @require_api_keys
    def export_scan(scan_id: int):
        body = request.get_json(silent=True) or {}
        fmt = body.get("format") or "pdf"
        chapters = body.get("chapters")
        file_id = store.start_export(scan_id, fmt, chapters)
        if not file_id:
            return jsonify({"error": "NotFound"}), 404
        # Android expects { "file": "<fileId>" }
        return jsonify({"file": file_id})

    @app.get("/scans/<int:scan_id>/export/<file_id>/status")
    @require_api_keys
    def export_status(scan_id: int, file_id: str):
        st = store.export_status(scan_id, file_id)
        if st is None:
            return jsonify({"error": "NotFound"}), 404
        return jsonify(st)

    @app.get("/scans/<int:scan_id>/export/<file_id>/download")
    @require_api_keys
    def export_download(scan_id: int, file_id: str):
        result = store.export_download(scan_id, file_id)
        if result is None:
            return jsonify({"error": "NotFound"}), 404
        data, mime, filename = result
        return Response(
            data,
            mimetype=mime,
            headers={
                "Content-Disposition": f'attachment; filename="{filename}"',
                "Content-Length": str(len(data)),
            },
        )

    # ---------------------------------------------------------------- plugins
    @app.get("/plugins/plugin/<int:plugin_id>")
    @require_api_keys
    def get_plugin(plugin_id: int):
        return jsonify(store.get_plugin(plugin_id))

    # -------------------------------------------------------------- templates
    @app.get("/editor/scan/templates")
    @require_api_keys
    def list_templates():
        return jsonify(store.list_templates())

    # ----------------------------------------------------------------- groups
    @app.get("/groups")
    @require_api_keys
    def list_groups():
        return jsonify(store.list_groups())

    @app.post("/groups")
    @require_api_keys
    def create_group():
        body = request.get_json(silent=True) or {}
        name = (body.get("name") or "").strip()
        if not name:
            return jsonify({"error": "BadRequest", "message": "name required"}), 400
        store.create_group(name)
        return empty_ok()

    @app.delete("/groups/<group_id>")
    @require_api_keys
    def delete_group(group_id: str):
        if not store.delete_group(group_id):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    # --------------------------------------------------------------- scanners
    @app.get("/scanners")
    @require_api_keys
    def list_scanners():
        return jsonify(store.list_scanners())

    # ----------------------------------------------------------- agent groups
    @app.get("/scanners/<scanner_id>/agent-groups")
    @require_api_keys
    def list_agent_groups(scanner_id: str):
        return jsonify(store.list_agent_groups(scanner_id))

    @app.post("/scanners/<scanner_id>/agent-groups")
    @require_api_keys
    def create_agent_group(scanner_id: str):
        body = request.get_json(silent=True) or {}
        name = (body.get("name") or "").strip()
        if not name:
            return jsonify({"error": "BadRequest", "message": "name required"}), 400
        store.create_agent_group(scanner_id, name)
        return empty_ok()

    @app.delete("/scanners/<scanner_id>/agent-groups/<group_id>")
    @require_api_keys
    def delete_agent_group(scanner_id: str, group_id: str):
        if not store.delete_agent_group(scanner_id, group_id):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    @app.get("/scanners/<scanner_id>/agent-groups/<group_id>/agents")
    @require_api_keys
    def list_agents_in_group(scanner_id: str, group_id: str):
        return jsonify(store.list_agents_in_group(scanner_id, group_id))

    @app.put("/scanners/<scanner_id>/agent-groups/<group_id>/agents/<int:agent_id>")
    @require_api_keys
    def add_agent_to_group(scanner_id: str, group_id: str, agent_id: int):
        if not store.add_agent_to_group(scanner_id, group_id, agent_id):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    @app.delete("/scanners/<scanner_id>/agent-groups/<group_id>/agents/<int:agent_id>")
    @require_api_keys
    def remove_agent_from_group(scanner_id: str, group_id: str, agent_id: int):
        if not store.remove_agent_from_group(scanner_id, group_id, agent_id):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    # ----------------------------------------------------------------- agents
    @app.get("/scanners/<scanner_id>/agents")
    @require_api_keys
    def list_agents(scanner_id: str):
        return jsonify(store.list_agents(scanner_id))

    @app.delete("/scanners/<scanner_id>/agents/<int:agent_id>")
    @require_api_keys
    def unlink_agent(scanner_id: str, agent_id: int):
        if not store.unlink_agent(scanner_id, agent_id):
            return jsonify({"error": "NotFound"}), 404
        return empty_ok()

    # ----------------------------------------------------------------- errors
    @app.errorhandler(404)
    def not_found(_e):
        return jsonify({"error": "NotFound", "path": request.path}), 404

    @app.errorhandler(405)
    def method_not_allowed(_e):
        return jsonify({"error": "MethodNotAllowed", "path": request.path}), 405

    @app.errorhandler(500)
    def server_error(e):
        log.exception("Internal error: %s", e)
        return jsonify({"error": "InternalError", "message": str(e)}), 500

    log.info(
        "Nessus mock ready — scans=%s agent_groups=%s agents≈%s reseed_hours=%s",
        settings.num_scans,
        settings.num_agent_groups,
        settings.num_agent_groups * settings.agents_per_group + settings.num_unlinked_agents,
        settings.reseed_interval_hours,
    )
    _start_reseed_scheduler()
    return app


# Module-level app for `flask run` / gunicorn "app:create_app()"
app = create_app()


if __name__ == "__main__":
    app.run(host=settings.host, port=settings.port, debug=settings.flask_debug)
