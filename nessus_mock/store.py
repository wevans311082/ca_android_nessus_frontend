"""
SQLite-backed mock Nessus data store.

All mutating API actions (create/delete groups, scans, agents, etc.) persist
across restarts. Demo seed data is loaded only when the database is empty
(or when RESET_DB_ON_START=1).
"""

from __future__ import annotations

import json
import logging
import random
import sqlite3
import threading
import time
import uuid
from copy import deepcopy
from pathlib import Path
from typing import Any, Iterator

from config import Settings
from pdf_report import build_scan_pdf

log = logging.getLogger("nessus-mock.store")

# Plugin catalogue used for findings + detail attributes
PLUGIN_CATALOG: list[dict[str, Any]] = [
    {
        "plugin_id": 19506,
        "plugin_name": "Nessus Scan Information",
        "severity": 0,
        "plugin_family": "Settings",
        "cve": None,
        "description": "This plugin displays information about the Nessus scan.",
        "solution": "n/a",
        "synopsis": "Information about the scan.",
        "see_also": "https://www.tenable.com",
        "risk_factor": "None",
    },
    {
        "plugin_id": 11219,
        "plugin_name": "Nessus SYN scanner",
        "severity": 0,
        "plugin_family": "Port scanners",
        "cve": None,
        "description": "This plugin is a SYN port scanner.",
        "solution": "n/a",
        "synopsis": "Port scan results.",
        "see_also": "",
        "risk_factor": "None",
    },
    {
        "plugin_id": 51192,
        "plugin_name": "SSL Certificate Cannot Be Trusted",
        "severity": 2,
        "plugin_family": "General",
        "cve": None,
        "description": "The server's SSL certificate cannot be trusted.",
        "solution": "Purchase or generate a proper certificate for this service.",
        "synopsis": "The SSL certificate chain is incomplete or untrusted.",
        "see_also": "https://www.sslshopper.com/ssl-checker.html",
        "risk_factor": "Medium",
    },
    {
        "plugin_id": 10407,
        "plugin_name": "X.509 Certificate Subject CN Does Not Match Host",
        "severity": 2,
        "plugin_family": "General",
        "cve": None,
        "description": "The Common Name in the SSL certificate does not match the hostname.",
        "solution": "Use a certificate that matches the hostname.",
        "synopsis": "Certificate CN mismatch.",
        "see_also": "",
        "risk_factor": "Medium",
    },
    {
        "plugin_id": 57582,
        "plugin_name": "SSL Self-Signed Certificate",
        "severity": 2,
        "plugin_family": "General",
        "cve": None,
        "description": "The SSL certificate is self-signed.",
        "solution": "Replace with a CA-signed certificate.",
        "synopsis": "Self-signed SSL certificate.",
        "see_also": "",
        "risk_factor": "Medium",
    },
    {
        "plugin_id": 108797,
        "plugin_name": "TLS Version 1.0 Protocol Detection",
        "severity": 2,
        "plugin_family": "Service detection",
        "cve": None,
        "description": "The remote service accepts connections encrypted using TLS 1.0.",
        "solution": "Disable TLS 1.0 and use TLS 1.2+.",
        "synopsis": "TLS 1.0 is enabled.",
        "see_also": "https://datatracker.ietf.org/doc/html/rfc8996",
        "risk_factor": "Medium",
    },
    {
        "plugin_id": 104743,
        "plugin_name": "TLS Version 1.1 Protocol Detection",
        "severity": 1,
        "plugin_family": "Service detection",
        "cve": None,
        "description": "TLS 1.1 is supported.",
        "solution": "Disable TLS 1.1.",
        "synopsis": "TLS 1.1 is enabled.",
        "see_also": "",
        "risk_factor": "Low",
    },
    {
        "plugin_id": 70658,
        "plugin_name": "SSH Weak Algorithms Supported",
        "severity": 2,
        "plugin_family": "Misc.",
        "cve": None,
        "description": "The remote SSH server is configured to allow weak algorithms.",
        "solution": "Disable weak ciphers and MACs.",
        "synopsis": "Weak SSH algorithms.",
        "see_also": "",
        "risk_factor": "Medium",
    },
    {
        "plugin_id": 41028,
        "plugin_name": "SSL Medium Strength Cipher Suites Supported",
        "severity": 2,
        "plugin_family": "General",
        "cve": None,
        "description": "Medium strength SSL ciphers are supported.",
        "solution": "Reconfigure to use only strong ciphers.",
        "synopsis": "Medium strength SSL ciphers.",
        "see_also": "",
        "risk_factor": "Medium",
    },
    {
        "plugin_id": 20007,
        "plugin_name": "SSL Version 2 and 3 Protocol Detection",
        "severity": 3,
        "plugin_family": "Service detection",
        "cve": "CVE-2014-3566",
        "description": "SSLv2/SSLv3 may be enabled (demo finding).",
        "solution": "Disable SSL 2.0 and 3.0.",
        "synopsis": "Legacy SSL protocols.",
        "see_also": "https://www.openssl.org/~bodo/ssl-poodle.pdf",
        "risk_factor": "High",
    },
    {
        "plugin_id": 57608,
        "plugin_name": "SMB Signing not required",
        "severity": 2,
        "plugin_family": "Misc.",
        "cve": None,
        "description": "Signing is not required on the remote SMB server.",
        "solution": "Enforce SMB signing.",
        "synopsis": "SMB signing not required.",
        "see_also": "",
        "risk_factor": "Medium",
    },
    {
        "plugin_id": 26928,
        "plugin_name": "SSL / TLS Renegotiation Handshakes MiTM Vulnerability",
        "severity": 3,
        "plugin_family": "Misc.",
        "cve": "CVE-2009-3555",
        "description": "The remote service allows insecure renegotiation.",
        "solution": "Apply vendor patches / disable renegotiation.",
        "synopsis": "TLS renegotiation issue.",
        "see_also": "",
        "risk_factor": "High",
    },
    {
        "plugin_id": 1000001,
        "plugin_name": "Demo Critical: Outdated OpenSSL (synthetic)",
        "severity": 4,
        "plugin_family": "Web Servers",
        "cve": "CVE-2014-0160",
        "description": "Synthetic critical finding for demo/review purposes only.",
        "solution": "Upgrade OpenSSL to a supported release.",
        "synopsis": "Outdated OpenSSL (demo).",
        "see_also": "https://www.openssl.org/news/secadv/20140407.txt",
        "risk_factor": "Critical",
    },
    {
        "plugin_id": 1000002,
        "plugin_name": "Demo High: Default credentials accepted (synthetic)",
        "severity": 3,
        "plugin_family": "Backdoors",
        "cve": None,
        "description": "Synthetic high finding: service accepted demo credentials.",
        "solution": "Change default passwords immediately.",
        "synopsis": "Default credentials (demo).",
        "see_also": "",
        "risk_factor": "High",
    },
    {
        "plugin_id": 1000003,
        "plugin_name": "Demo Medium: Missing security headers",
        "severity": 2,
        "plugin_family": "Web Servers",
        "cve": None,
        "description": "HTTP security headers are missing on the web application.",
        "solution": "Add CSP, HSTS, X-Content-Type-Options, etc.",
        "synopsis": "Missing HTTP security headers.",
        "see_also": "https://owasp.org/www-project-secure-headers/",
        "risk_factor": "Medium",
    },
    {
        "plugin_id": 1000004,
        "plugin_name": "Demo Low: ICMP timestamp reply",
        "severity": 1,
        "plugin_family": "General",
        "cve": "CVE-1999-0524",
        "description": "The remote host answers to an ICMP timestamp request.",
        "solution": "Filter ICMP timestamp requests.",
        "synopsis": "ICMP timestamp.",
        "see_also": "",
        "risk_factor": "Low",
    },
]

SCAN_STATUSES = ["completed", "completed", "completed", "running", "paused", "empty", "canceled"]
PLATFORMS = [
    ("WINDOWS", "Windows Server 2022"),
    ("LINUX", "Ubuntu 22.04"),
    ("LINUX", "RHEL 9"),
    ("MACOS", "macOS 14"),
]

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS folders (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT
);

CREATE TABLE IF NOT EXISTS scans (
    id INTEGER PRIMARY KEY,
    name TEXT NOT NULL,
    status TEXT,
    folder_id INTEGER,
    owner TEXT,
    creation_date INTEGER,
    last_modification_date INTEGER,
    payload TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS plugins (
    plugin_id INTEGER PRIMARY KEY,
    payload TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS user_groups (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    owner TEXT
);

CREATE TABLE IF NOT EXISTS agent_groups (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS agents (
    id INTEGER PRIMARY KEY,
    uuid TEXT,
    name TEXT NOT NULL,
    status TEXT,
    last_connect INTEGER,
    last_scanned INTEGER,
    platform TEXT,
    distro TEXT,
    ip TEXT,
    core_build TEXT,
    linked_on INTEGER
);

CREATE TABLE IF NOT EXISTS agent_group_members (
    group_id TEXT NOT NULL,
    agent_id INTEGER NOT NULL,
    PRIMARY KEY (group_id, agent_id),
    FOREIGN KEY (group_id) REFERENCES agent_groups(id) ON DELETE CASCADE,
    FOREIGN KEY (agent_id) REFERENCES agents(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS scanners (
    id TEXT PRIMARY KEY,
    name TEXT,
    type TEXT,
    status TEXT,
    uuid TEXT,
    last_connect INTEGER
);

CREATE TABLE IF NOT EXISTS templates (
    uuid TEXT PRIMARY KEY,
    name TEXT,
    title TEXT,
    description TEXT,
    type TEXT,
    cloud_only INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS exports (
    file_id TEXT PRIMARY KEY,
    scan_id INTEGER NOT NULL,
    format TEXT NOT NULL,
    chapters TEXT,
    created REAL NOT NULL,
    status TEXT NOT NULL
);
"""


def _j(obj: Any) -> str:
    return json.dumps(obj, separators=(",", ":"), ensure_ascii=False)


def _loads(raw: str | None, default: Any = None) -> Any:
    if raw is None or raw == "":
        return default
    return json.loads(raw)


class MockStore:
    """Persistent store with the same public API as the original in-memory mock."""

    def __init__(self, settings: Settings):
        self.settings = settings
        self._lock = threading.RLock()
        self._rng = random.Random(42)
        self.now = int(time.time())

        db_path = Path(settings.database_path)
        db_path.parent.mkdir(parents=True, exist_ok=True)

        if settings.reset_db_on_start and db_path.exists():
            db_path.unlink()
            log.warning("RESET_DB_ON_START=1 — deleted %s", db_path)

        self._db_path = str(db_path)
        self._conn = sqlite3.connect(self._db_path, check_same_thread=False)
        self._conn.row_factory = sqlite3.Row
        self._conn.execute("PRAGMA foreign_keys = ON")
        self._conn.execute("PRAGMA journal_mode = WAL")
        self._init_schema()

        if self._is_empty():
            log.info("Database empty — seeding demo data into %s", self._db_path)
            self._seed()
        else:
            log.info("Loaded existing database %s", self._db_path)
            # If the process was down past the reseed window, clean up on boot
            if self.due_for_reseed():
                log.info("Reseed interval elapsed while offline — wiping and re-seeding now")
                self.reset_and_reseed()

    # ------------------------------------------------------------------ db
    def _init_schema(self) -> None:
        with self._lock:
            self._conn.executescript(SCHEMA)
            self._conn.commit()

    def _is_empty(self) -> bool:
        with self._lock:
            row = self._conn.execute("SELECT COUNT(*) AS c FROM scans").fetchone()
            return int(row["c"]) == 0

    def last_reseed_at(self) -> float | None:
        """Unix timestamp of last successful seed/reseed, if known."""
        with self._lock:
            raw = self._meta_get("last_reseed_at") or self._meta_get("seeded_at")
            if raw is None:
                return None
            try:
                return float(raw)
            except ValueError:
                return None

    def reseed_interval_seconds(self) -> float:
        hours = float(self.settings.reseed_interval_hours or 0)
        return max(0.0, hours * 3600.0)

    def due_for_reseed(self) -> bool:
        interval = self.reseed_interval_seconds()
        if interval <= 0:
            return False
        last = self.last_reseed_at()
        if last is None:
            return True
        return (time.time() - last) >= interval

    def seconds_until_reseed(self) -> float | None:
        """Seconds until next automatic reseed, or None if disabled."""
        interval = self.reseed_interval_seconds()
        if interval <= 0:
            return None
        last = self.last_reseed_at() or time.time()
        remaining = interval - (time.time() - last)
        return max(0.0, remaining)

    def reset_and_reseed(self) -> None:
        """Wipe all tables and load a fresh demo dataset (thread-safe)."""
        with self._lock:
            log.warning("Resetting database and re-seeding demo data…")
            # Order matters under foreign keys
            self._conn.executescript(
                """
                DELETE FROM exports;
                DELETE FROM agent_group_members;
                DELETE FROM agents;
                DELETE FROM agent_groups;
                DELETE FROM user_groups;
                DELETE FROM scans;
                DELETE FROM folders;
                DELETE FROM plugins;
                DELETE FROM scanners;
                DELETE FROM templates;
                DELETE FROM meta;
                """
            )
            self._conn.commit()
            # Fresh RNG so each reseed is still deterministic but not identical mid-session chaos
            self._rng = random.Random(42)
            self.now = int(time.time())
            self._seed()
            # _seed already commits; stamp reseed time explicitly
            self._meta_set("last_reseed_at", str(time.time()))
            self._commit()
            log.info("Re-seed complete at %s", time.strftime("%Y-%m-%d %H:%M:%S UTC", time.gmtime()))

    def _execute(self, sql: str, params: tuple | list = ()) -> sqlite3.Cursor:
        return self._conn.execute(sql, params)

    def _executemany(self, sql: str, seq: list) -> None:
        self._conn.executemany(sql, seq)

    def _commit(self) -> None:
        self._conn.commit()

    def _meta_get(self, key: str, default: str | None = None) -> str | None:
        row = self._execute("SELECT value FROM meta WHERE key = ?", (key,)).fetchone()
        return row["value"] if row else default

    def _meta_set(self, key: str, value: str) -> None:
        self._execute(
            "INSERT INTO meta(key, value) VALUES(?, ?) ON CONFLICT(key) DO UPDATE SET value = excluded.value",
            (key, value),
        )

    def _next_id(self, name: str, start: int) -> int:
        """Atomically allocate the next integer id for a sequence name."""
        raw = self._meta_get(f"seq_{name}")
        if raw is None:
            n = start
        else:
            n = int(raw)
        self._meta_set(f"seq_{name}", str(n + 1))
        return n

    # ---------------------------------------------------------------- seed
    def _seed(self) -> None:
        s = self.settings
        with self._lock:
            # folders
            self._executemany(
                "INSERT INTO folders(id, name, type) VALUES(?,?,?)",
                [
                    (1, "My Scans", "main"),
                    (2, "Play Review Demo", "custom"),
                ],
            )

            # plugins
            for p in PLUGIN_CATALOG:
                self._execute(
                    "INSERT OR REPLACE INTO plugins(plugin_id, payload) VALUES(?,?)",
                    (p["plugin_id"], _j(p)),
                )

            # scanners
            scanners = [
                ("1", "Local Nessus (Demo)", "local", "on", str(uuid.uuid4()), self.now - 60),
                ("null", "Cloud / Default Scanner (Demo)", "cloud", "on", str(uuid.uuid4()), self.now - 120),
            ]
            for i in range(3, s.num_scanners + 1):
                scanners.append(
                    (
                        str(i),
                        f"Demo Scanner {i}",
                        "remote",
                        "on" if i % 2 else "off",
                        str(uuid.uuid4()),
                        self.now - i * 300,
                    )
                )
            self._executemany(
                "INSERT INTO scanners(id, name, type, status, uuid, last_connect) VALUES(?,?,?,?,?,?)",
                scanners,
            )

            # templates
            template_defs = [
                ("basic-net", "basic", "Basic Network Scan", "local", "Standard network vulnerability scan"),
                ("discovery", "discovery", "Host Discovery", "local", "Discover live hosts"),
                ("webapp", "webapp", "Web Application Tests", "local", "Web application checks"),
                ("agent-basic", "agent", "Basic Agent Scan", "agent", "Agent-based vulnerability scan"),
                ("agent-os", "agent_os", "Agent OS Scan", "agent", "Operating system agent scan"),
                ("compliance", "compliance", "Compliance Checks", "local", "Configuration compliance"),
                ("malware", "malware", "Malware Scan", "local", "Malware detection"),
                ("advanced", "advanced", "Advanced Scan", "local", "Full advanced scan policy"),
                ("credentialed", "credentialed", "Credentialed Patch Audit", "local", "Authenticated patch audit"),
                ("mobile", "mobile", "Mobile Device Scan", "local", "Mobile posture (demo)"),
            ]
            for i in range(s.num_templates):
                key, name, title, typ, desc = template_defs[i % len(template_defs)]
                uid = f"template-uuid-{key}-{i + 1}"
                self._execute(
                    "INSERT INTO templates(uuid, name, title, description, type, cloud_only) VALUES(?,?,?,?,?,0)",
                    (
                        uid,
                        name if i < len(template_defs) else f"{name}-{i + 1}",
                        title if i < len(template_defs) else f"{title} {i + 1}",
                        desc,
                        typ,
                    ),
                )

            # user groups
            for i in range(1, s.num_groups + 1):
                self._execute(
                    "INSERT INTO user_groups(id, name, owner) VALUES(?,?,?)",
                    (str(i), f"Review Group {i}", "demo-admin"),
                )
            self._meta_set("seq_group", str(s.num_groups + 1))

            # agents + agent groups
            agent_id = 1
            for g in range(1, s.num_agent_groups + 1):
                ag_id = str(uuid.uuid4()) if g > 2 else str(g)
                self._execute(
                    "INSERT INTO agent_groups(id, name) VALUES(?,?)",
                    (ag_id, f"Agent Group {g}"),
                )
                for _ in range(s.agents_per_group):
                    a = self._make_agent_dict(agent_id)
                    self._insert_agent(a)
                    self._execute(
                        "INSERT INTO agent_group_members(group_id, agent_id) VALUES(?,?)",
                        (ag_id, agent_id),
                    )
                    agent_id += 1

            for _ in range(s.num_unlinked_agents):
                a = self._make_agent_dict(agent_id)
                a["status"] = self._rng.choice(["on", "off", "online", "offline"])
                self._insert_agent(a)
                agent_id += 1
            self._meta_set("seq_agent", str(agent_id))

            # scans
            history_seq = 9000
            self._meta_set("seq_history", str(history_seq))
            for i in range(1, s.num_scans + 1):
                sid = 100 + i
                sc = self._build_scan_document(sid, i)
                self._insert_scan_row(sc)
            self._meta_set("seq_scan", str(100 + s.num_scans + 1))
            ts = str(time.time())
            self._meta_set("seeded_at", ts)
            self._meta_set("last_reseed_at", ts)
            self._commit()

    def _make_agent_dict(self, agent_id: int) -> dict[str, Any]:
        platform, distro = self._rng.choice(PLATFORMS)
        return {
            "id": agent_id,
            "uuid": str(uuid.uuid4()),
            "name": f"demo-agent-{agent_id:03d}",
            "status": self._rng.choice(["on", "online", "off", "offline"]),
            "last_connect": self.now - self._rng.randint(60, 86400 * 7),
            "last_scanned": self.now - self._rng.randint(3600, 86400 * 14),
            "platform": platform,
            "distro": distro,
            "ip": f"10.{self._rng.randint(0, 20)}.{self._rng.randint(0, 255)}.{self._rng.randint(1, 254)}",
            "core_build": f"10.{self._rng.randint(1, 9)}.{self._rng.randint(0, 5)}",
            "linked_on": self.now - self._rng.randint(86400, 86400 * 365),
        }

    def _insert_agent(self, a: dict[str, Any]) -> None:
        self._execute(
            """INSERT INTO agents(id, uuid, name, status, last_connect, last_scanned,
               platform, distro, ip, core_build, linked_on)
               VALUES(?,?,?,?,?,?,?,?,?,?,?)""",
            (
                a["id"],
                a.get("uuid"),
                a["name"],
                a.get("status"),
                a.get("last_connect"),
                a.get("last_scanned"),
                a.get("platform"),
                a.get("distro"),
                a.get("ip"),
                a.get("core_build"),
                a.get("linked_on"),
            ),
        )

    def _pick_vulns(self, n: int) -> list[dict[str, Any]]:
        picks = self._rng.sample(PLUGIN_CATALOG, k=min(n, len(PLUGIN_CATALOG)))
        out = []
        for p in picks:
            out.append(
                {
                    "plugin_id": p["plugin_id"],
                    "plugin_name": p["plugin_name"],
                    "severity": p["severity"],
                    "plugin_family": p["plugin_family"],
                    "count": self._rng.randint(1, 8),
                    "cve": p.get("cve"),
                }
            )
        return out

    def _build_scan_document(self, scan_id: int, index: int) -> dict[str, Any]:
        s = self.settings
        status = SCAN_STATUSES[(index - 1) % len(SCAN_STATUSES)]
        labels = [
            "Perimeter",
            "Internal",
            "DMZ",
            "Cloud Assets",
            "PCI Scope",
            "Dev Lab",
            "Wi‑Fi Segment",
            "OT Lab",
            "Remote Sites",
            "Review Sample",
        ]
        name = f"Demo Scan {index:02d} — {labels[(index - 1) % len(labels)]}"
        hosts = []
        host_vulns: dict[str, list[dict[str, Any]]] = {}
        agg: dict[int, dict[str, Any]] = {}

        for h in range(1, s.num_hosts_per_scan + 1):
            host_id = h
            vulns = self._pick_vulns(s.num_vulns_per_host)
            host_vulns[str(host_id)] = vulns
            counts = {0: 0, 1: 0, 2: 0, 3: 0, 4: 0}
            for v in vulns:
                sev = int(v.get("severity") or 0)
                counts[sev] = counts.get(sev, 0) + 1
                pid = v["plugin_id"]
                if pid not in agg:
                    agg[pid] = deepcopy(v)
                else:
                    agg[pid]["count"] = (agg[pid].get("count") or 0) + (v.get("count") or 1)
            hosts.append(
                {
                    "host_id": host_id,
                    "hostname": f"host-{scan_id}-{h}.demo.local",
                    "progress": "100" if status == "completed" else str(self._rng.randint(10, 95)),
                    "critical": counts.get(4, 0),
                    "high": counts.get(3, 0),
                    "medium": counts.get(2, 0),
                    "low": counts.get(1, 0),
                    "info": counts.get(0, 0),
                }
            )

        history = []
        hist_start = int(self._meta_get("seq_history", "9000") or "9000")
        for j in range(s.num_history_per_scan):
            hid = hist_start + j
            history.append(
                {
                    "history_id": hid,
                    "creation_date": self.now - (j + 1) * 86400,
                    "status": "completed" if j > 0 else status,
                    "owner": "demo-admin",
                }
            )
        self._meta_set("seq_history", str(hist_start + s.num_history_per_scan))

        remediations = [
            {"value": "Upgrade outdated TLS configurations across HTTPS endpoints.", "vulns": 12},
            {"value": "Enforce SMB signing on all file servers.", "vulns": 5},
            {"value": "Replace self-signed certificates with trusted CA certificates.", "vulns": 8},
            {"value": "Disable weak SSH algorithms (demo remediation).", "vulns": 4},
            {"value": "Apply missing OS security patches (synthetic).", "vulns": 15},
        ]

        scanner_name = "Local Nessus (Demo)"
        return {
            "id": scan_id,
            "name": name,
            "status": status,
            "folder_id": 2 if index % 2 == 0 else 1,
            "owner": "demo-admin",
            "creation_date": self.now - index * 86400,
            "last_modification_date": self.now - index * 3600,
            "info": {
                "name": name,
                "status": status,
                "policy": "Demo Advanced Scan",
                "scanner_name": scanner_name,
            },
            "hosts": hosts,
            "host_vulns": host_vulns,
            "vulnerabilities": list(agg.values()),
            "remediations": {"remediations": remediations},
            "history": history,
        }

    def _insert_scan_row(self, sc: dict[str, Any]) -> None:
        payload = {
            "info": sc.get("info"),
            "hosts": sc.get("hosts"),
            "host_vulns": sc.get("host_vulns"),
            "vulnerabilities": sc.get("vulnerabilities"),
            "remediations": sc.get("remediations"),
            "history": sc.get("history"),
        }
        self._execute(
            """INSERT INTO scans(id, name, status, folder_id, owner, creation_date,
               last_modification_date, payload) VALUES(?,?,?,?,?,?,?,?)""",
            (
                sc["id"],
                sc["name"],
                sc.get("status"),
                sc.get("folder_id"),
                sc.get("owner"),
                sc.get("creation_date"),
                sc.get("last_modification_date"),
                _j(payload),
            ),
        )

    def _scan_from_row(self, row: sqlite3.Row) -> dict[str, Any]:
        payload = _loads(row["payload"], {})
        return {
            "id": row["id"],
            "name": row["name"],
            "status": row["status"],
            "folder_id": row["folder_id"],
            "owner": row["owner"],
            "creation_date": row["creation_date"],
            "last_modification_date": row["last_modification_date"],
            **payload,
        }

    def _update_scan_payload(self, sc: dict[str, Any]) -> None:
        payload = {
            "info": sc.get("info"),
            "hosts": sc.get("hosts"),
            "host_vulns": sc.get("host_vulns"),
            "vulnerabilities": sc.get("vulnerabilities"),
            "remediations": sc.get("remediations"),
            "history": sc.get("history"),
        }
        self._execute(
            """UPDATE scans SET name=?, status=?, folder_id=?, owner=?,
               creation_date=?, last_modification_date=?, payload=? WHERE id=?""",
            (
                sc["name"],
                sc.get("status"),
                sc.get("folder_id"),
                sc.get("owner"),
                sc.get("creation_date"),
                sc.get("last_modification_date"),
                _j(payload),
                sc["id"],
            ),
        )

    def _agent_from_row(self, row: sqlite3.Row) -> dict[str, Any]:
        return {
            "id": row["id"],
            "uuid": row["uuid"],
            "name": row["name"],
            "status": row["status"],
            "last_connect": row["last_connect"],
            "last_scanned": row["last_scanned"],
            "platform": row["platform"],
            "distro": row["distro"],
            "ip": row["ip"],
            "core_build": row["core_build"],
            "linked_on": row["linked_on"],
        }

    # ---------------------------------------------------------------- scans
    def list_scans(self) -> dict[str, Any]:
        with self._lock:
            rows = self._execute(
                "SELECT id, name, status, folder_id, owner, creation_date, last_modification_date "
                "FROM scans ORDER BY id"
            ).fetchall()
            scans = [
                {
                    "id": r["id"],
                    "name": r["name"],
                    "status": r["status"],
                    "folder_id": r["folder_id"],
                    "owner": r["owner"],
                    "creation_date": r["creation_date"],
                    "last_modification_date": r["last_modification_date"],
                }
                for r in rows
            ]
            folders = [
                {"id": f["id"], "name": f["name"], "type": f["type"]}
                for f in self._execute("SELECT id, name, type FROM folders ORDER BY id").fetchall()
            ]
            return {"scans": scans, "folders": folders}

    def get_scan(self, scan_id: int, history_id: int | None = None) -> dict[str, Any] | None:
        with self._lock:
            row = self._execute("SELECT * FROM scans WHERE id = ?", (scan_id,)).fetchone()
            if not row:
                return None
            sc = self._scan_from_row(row)
            return {
                "info": deepcopy(sc.get("info")),
                "vulnerabilities": deepcopy(sc.get("vulnerabilities") or []),
                "remediations": deepcopy(sc.get("remediations")),
                "hosts": deepcopy(sc.get("hosts") or []),
                "history": deepcopy(sc.get("history") or []),
            }

    def get_scan_host(
        self, scan_id: int, host_id: int, history_id: int | None = None
    ) -> dict[str, Any] | None:
        with self._lock:
            row = self._execute("SELECT * FROM scans WHERE id = ?", (scan_id,)).fetchone()
            if not row:
                return None
            sc = self._scan_from_row(row)
            host_vulns = sc.get("host_vulns") or {}
            # keys may be str from JSON
            vulns = host_vulns.get(str(host_id)) or host_vulns.get(host_id) or []
            return {"vulnerabilities": deepcopy(vulns)}

    def set_scan_status(self, scan_id: int, status: str) -> bool:
        with self._lock:
            row = self._execute("SELECT * FROM scans WHERE id = ?", (scan_id,)).fetchone()
            if not row:
                return False
            sc = self._scan_from_row(row)
            sc["status"] = status
            info = sc.get("info") or {}
            info["status"] = status
            sc["info"] = info
            sc["last_modification_date"] = int(time.time())
            self._update_scan_payload(sc)
            self._commit()
            return True

    def delete_scan(self, scan_id: int) -> bool:
        with self._lock:
            cur = self._execute("DELETE FROM scans WHERE id = ?", (scan_id,))
            self._execute("DELETE FROM exports WHERE scan_id = ?", (scan_id,))
            self._commit()
            return cur.rowcount > 0

    def rename_scan(self, scan_id: int, name: str) -> bool:
        with self._lock:
            row = self._execute("SELECT * FROM scans WHERE id = ?", (scan_id,)).fetchone()
            if not row:
                return False
            sc = self._scan_from_row(row)
            sc["name"] = name
            info = sc.get("info") or {}
            info["name"] = name
            sc["info"] = info
            sc["last_modification_date"] = int(time.time())
            self._update_scan_payload(sc)
            self._commit()
            return True

    def create_scan(self, body: dict[str, Any]) -> dict[str, Any]:
        with self._lock:
            settings_body = body.get("settings") or {}
            name = settings_body.get("name") or "New Demo Scan"
            sid = self._next_id("scan", 2000)
            sc = self._build_scan_document(sid, sid)
            sc["name"] = name
            sc["info"]["name"] = name
            sc["status"] = "running" if settings_body.get("launch_now") else "empty"
            sc["info"]["status"] = sc["status"]
            sc["creation_date"] = int(time.time())
            sc["last_modification_date"] = sc["creation_date"]
            self._insert_scan_row(sc)
            self._commit()
            return {
                "scan": {
                    "id": sid,
                    "name": name,
                    "status": sc["status"],
                    "folder_id": sc.get("folder_id"),
                    "owner": sc.get("owner"),
                    "creation_date": sc.get("creation_date"),
                    "last_modification_date": sc.get("last_modification_date"),
                }
            }

    # -------------------------------------------------------------- plugins
    def get_plugin(self, plugin_id: int) -> dict[str, Any]:
        with self._lock:
            row = self._execute(
                "SELECT payload FROM plugins WHERE plugin_id = ?", (plugin_id,)
            ).fetchone()
            if row:
                p = _loads(row["payload"], {})
            else:
                p = {
                    "plugin_id": plugin_id,
                    "plugin_name": f"Unknown plugin {plugin_id}",
                    "description": "No detailed description in demo catalogue.",
                    "solution": "n/a",
                    "synopsis": "Demo plugin",
                    "see_also": "",
                    "risk_factor": "None",
                    "plugin_family": "General",
                    "cve": None,
                    "severity": 0,
                }
            attrs = [
                {"attribute_name": "plugin_name", "attribute_value": p.get("plugin_name", "")},
                {"attribute_name": "description", "attribute_value": p.get("description", "")},
                {"attribute_name": "solution", "attribute_value": p.get("solution", "")},
                {"attribute_name": "synopsis", "attribute_value": p.get("synopsis", "")},
                {"attribute_name": "see_also", "attribute_value": p.get("see_also") or ""},
                {"attribute_name": "risk_factor", "attribute_value": p.get("risk_factor", "None")},
                {"attribute_name": "plugin_type", "attribute_value": "remote"},
                {"attribute_name": "fname", "attribute_value": f"demo_{plugin_id}.nasl"},
            ]
            if p.get("cve"):
                attrs.append({"attribute_name": "cve", "attribute_value": p["cve"]})
            return {"attributes": attrs}

    # -------------------------------------------------------------- export
    def start_export(self, scan_id: int, fmt: str, chapters: str | None) -> str | None:
        with self._lock:
            exists = self._execute("SELECT 1 FROM scans WHERE id = ?", (scan_id,)).fetchone()
            if not exists:
                return None
            file_id = str(uuid.uuid4())
            self._execute(
                "INSERT INTO exports(file_id, scan_id, format, chapters, created, status) VALUES(?,?,?,?,?,?)",
                (file_id, scan_id, (fmt or "pdf").lower(), chapters, time.time(), "loading"),
            )
            self._commit()
            return file_id

    def export_status(self, scan_id: int, file_id: str) -> dict[str, Any] | None:
        with self._lock:
            row = self._execute(
                "SELECT * FROM exports WHERE file_id = ? AND scan_id = ?",
                (file_id, scan_id),
            ).fetchone()
            if not row:
                return None
            status = row["status"]
            if time.time() - float(row["created"]) >= self.settings.export_ready_seconds:
                status = "ready"
                self._execute(
                    "UPDATE exports SET status = ? WHERE file_id = ?",
                    ("ready", file_id),
                )
                self._commit()
            return {"status": status}

    def export_download(self, scan_id: int, file_id: str) -> tuple[bytes, str, str] | None:
        with self._lock:
            row = self._execute(
                "SELECT * FROM exports WHERE file_id = ? AND scan_id = ?",
                (file_id, scan_id),
            ).fetchone()
            if not row:
                return None
            scan_row = self._execute("SELECT * FROM scans WHERE id = ?", (scan_id,)).fetchone()
            if not scan_row:
                return None
            sc = self._scan_from_row(scan_row)
            self._execute("UPDATE exports SET status = ? WHERE file_id = ?", ("ready", file_id))
            self._commit()
            fmt = row["format"] or "pdf"
            data = build_scan_pdf(sc, chapters=row["chapters"], fmt=fmt)
            mime = {
                "pdf": "application/pdf",
                "csv": "text/csv",
                "html": "text/html",
                "htm": "text/html",
                "nessus": "application/xml",
            }.get(fmt, "application/octet-stream")
            fname = f"scan-{scan_id}-{file_id[:8]}.{fmt if fmt != 'htm' else 'html'}"
            return data, mime, fname

    # -------------------------------------------------------------- groups
    def list_groups(self) -> dict[str, Any]:
        with self._lock:
            rows = self._execute(
                "SELECT id, name, owner FROM user_groups ORDER BY CAST(id AS INTEGER), id"
            ).fetchall()
            return {
                "groups": [
                    {"id": r["id"], "name": r["name"], "owner": r["owner"]} for r in rows
                ]
            }

    def create_group(self, name: str) -> dict[str, Any]:
        with self._lock:
            gid = str(self._next_id("group", 1))
            g = {"id": gid, "name": name.strip(), "owner": "demo-admin"}
            self._execute(
                "INSERT INTO user_groups(id, name, owner) VALUES(?,?,?)",
                (g["id"], g["name"], g["owner"]),
            )
            self._commit()
            return g

    def delete_group(self, group_id: str) -> bool:
        with self._lock:
            cur = self._execute("DELETE FROM user_groups WHERE id = ?", (str(group_id),))
            self._commit()
            return cur.rowcount > 0

    # ----------------------------------------------------------- scanners
    def list_scanners(self) -> dict[str, Any]:
        with self._lock:
            rows = self._execute(
                "SELECT id, name, type, status, uuid, last_connect FROM scanners"
            ).fetchall()
            return {
                "scanners": [
                    {
                        "id": r["id"],
                        "name": r["name"],
                        "type": r["type"],
                        "status": r["status"],
                        "uuid": r["uuid"],
                        "last_connect": r["last_connect"],
                    }
                    for r in rows
                ]
            }

    def _norm_scanner(self, scanner_id: str) -> str:
        return scanner_id if scanner_id is not None else "1"

    # ------------------------------------------------------- agent groups
    def list_agent_groups(self, scanner_id: str) -> dict[str, Any]:
        with self._lock:
            self._norm_scanner(scanner_id)
            rows = self._execute("SELECT id, name FROM agent_groups ORDER BY name").fetchall()
            groups = []
            for r in rows:
                cnt = self._execute(
                    "SELECT COUNT(*) AS c FROM agent_group_members WHERE group_id = ?",
                    (r["id"],),
                ).fetchone()["c"]
                groups.append({"id": r["id"], "name": r["name"], "agents_count": int(cnt)})
            return {"groups": groups}

    def create_agent_group(self, scanner_id: str, name: str) -> dict[str, Any]:
        with self._lock:
            self._norm_scanner(scanner_id)
            ag_id = str(uuid.uuid4())
            self._execute(
                "INSERT INTO agent_groups(id, name) VALUES(?,?)",
                (ag_id, name.strip()),
            )
            self._commit()
            return {"id": ag_id, "name": name.strip(), "agents_count": 0}

    def delete_agent_group(self, scanner_id: str, group_id: str) -> bool:
        with self._lock:
            self._norm_scanner(scanner_id)
            self._execute(
                "DELETE FROM agent_group_members WHERE group_id = ?", (str(group_id),)
            )
            cur = self._execute("DELETE FROM agent_groups WHERE id = ?", (str(group_id),))
            self._commit()
            return cur.rowcount > 0

    def list_agents_in_group(self, scanner_id: str, group_id: str) -> dict[str, Any]:
        with self._lock:
            self._norm_scanner(scanner_id)
            rows = self._execute(
                """SELECT a.* FROM agents a
                   INNER JOIN agent_group_members m ON m.agent_id = a.id
                   WHERE m.group_id = ?
                   ORDER BY a.id""",
                (str(group_id),),
            ).fetchall()
            return {"agents": [self._agent_from_row(r) for r in rows]}

    def add_agent_to_group(self, scanner_id: str, group_id: str, agent_id: int) -> bool:
        with self._lock:
            self._norm_scanner(scanner_id)
            g = self._execute(
                "SELECT 1 FROM agent_groups WHERE id = ?", (str(group_id),)
            ).fetchone()
            a = self._execute("SELECT 1 FROM agents WHERE id = ?", (agent_id,)).fetchone()
            if not g or not a:
                return False
            self._execute(
                "INSERT OR IGNORE INTO agent_group_members(group_id, agent_id) VALUES(?,?)",
                (str(group_id), agent_id),
            )
            self._commit()
            return True

    def remove_agent_from_group(self, scanner_id: str, group_id: str, agent_id: int) -> bool:
        with self._lock:
            self._norm_scanner(scanner_id)
            cur = self._execute(
                "DELETE FROM agent_group_members WHERE group_id = ? AND agent_id = ?",
                (str(group_id), agent_id),
            )
            self._commit()
            return cur.rowcount > 0

    def list_agents(self, scanner_id: str) -> dict[str, Any]:
        with self._lock:
            self._norm_scanner(scanner_id)
            rows = self._execute("SELECT * FROM agents ORDER BY id").fetchall()
            return {"agents": [self._agent_from_row(r) for r in rows]}

    def unlink_agent(self, scanner_id: str, agent_id: int) -> bool:
        with self._lock:
            self._norm_scanner(scanner_id)
            self._execute(
                "DELETE FROM agent_group_members WHERE agent_id = ?", (agent_id,)
            )
            cur = self._execute("DELETE FROM agents WHERE id = ?", (agent_id,))
            self._commit()
            return cur.rowcount > 0

    # ---------------------------------------------------------- templates
    def list_templates(self) -> dict[str, Any]:
        with self._lock:
            rows = self._execute(
                "SELECT uuid, name, title, description, type, cloud_only FROM templates"
            ).fetchall()
            return {
                "templates": [
                    {
                        "uuid": r["uuid"],
                        "name": r["name"],
                        "title": r["title"],
                        "description": r["description"],
                        "type": r["type"],
                        "cloud_only": bool(r["cloud_only"]),
                    }
                    for r in rows
                ]
            }
