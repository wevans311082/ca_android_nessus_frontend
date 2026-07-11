"""Configuration from environment / .env for the Nessus mock server."""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()


def _int(name: str, default: int) -> int:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    try:
        return max(0, int(raw))
    except ValueError:
        return default


def _bool(name: str, default: bool = False) -> bool:
    raw = os.getenv(name)
    if raw is None or raw.strip() == "":
        return default
    return raw.strip().lower() in ("1", "true", "yes", "on")


@dataclass(frozen=True)
class Settings:
    host: str
    port: int
    access_key: str
    secret_key: str
    database_path: str
    reset_db_on_start: bool
    # Hours between automatic wipe+reseed (0 = disabled). Default 6 for Play demo hygiene.
    reseed_interval_hours: float
    num_scans: int
    num_groups: int
    num_agent_groups: int
    agents_per_group: int
    num_unlinked_agents: int
    num_hosts_per_scan: int
    num_vulns_per_host: int
    num_history_per_scan: int
    num_scanners: int
    num_templates: int
    export_ready_seconds: float
    log_level: str
    flask_debug: bool


def load_settings() -> Settings:
    default_db = str(Path(__file__).resolve().parent / "data" / "nessus_mock.db")
    return Settings(
        host=os.getenv("HOST", "0.0.0.0"),
        port=_int("PORT", 8834),
        access_key=os.getenv("NESSUS_ACCESS_KEY", "demo-access-key-reviewer"),
        secret_key=os.getenv("NESSUS_SECRET_KEY", "demo-secret-key-reviewer"),
        database_path=os.getenv("DATABASE_PATH", default_db),
        reset_db_on_start=_bool("RESET_DB_ON_START", False),
        reseed_interval_hours=float(os.getenv("RESEED_INTERVAL_HOURS", "6")),
        num_scans=_int("NUM_SCANS", 10),
        num_groups=_int("NUM_GROUPS", 10),
        num_agent_groups=_int("NUM_AGENT_GROUPS", 10),
        agents_per_group=_int("AGENTS_PER_GROUP", 10),
        num_unlinked_agents=_int("NUM_UNLINKED_AGENTS", 15),
        num_hosts_per_scan=_int("NUM_HOSTS_PER_SCAN", 8),
        num_vulns_per_host=_int("NUM_VULNS_PER_HOST", 12),
        num_history_per_scan=_int("NUM_HISTORY_PER_SCAN", 3),
        num_scanners=max(1, _int("NUM_SCANNERS", 3)),
        num_templates=max(1, _int("NUM_TEMPLATES", 8)),
        export_ready_seconds=float(os.getenv("EXPORT_READY_SECONDS", "2")),
        log_level=os.getenv("LOG_LEVEL", "INFO").upper(),
        flask_debug=_bool("FLASK_DEBUG", False),
    )
