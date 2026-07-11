# CyberAsk Nessus Mock (Play Store review demo)

Standalone **Flask** service that emulates the Nessus / Tenable REST API endpoints used by **CyberAsk Scanner** (Android).

Use this when Google Play asks for a demo backend — **do not** give reviewers access to a real Nessus deployment.

> This folder is independent of the Android app source. Nothing under `app/` is modified by this project.

---

## Persistence

Data is stored in **SQLite** (not only in memory):

| Action | Persists after restart? |
|--------|-------------------------|
| Create / delete user groups | Yes |
| Create / delete agent groups | Yes |
| Add / remove / unlink agents | Yes |
| Create / delete / rename scans | Yes |
| Start / stop / pause / resume | Yes |
| Report exports (metadata) | Yes |

- Path: `DATABASE_PATH` (default `./data/nessus_mock.db`; Docker: `/data/nessus_mock.db` on volume `nessus_data`)
- **Seed once**: demo data is generated only when the DB is empty
- **Auto reseed every 6 hours** (`RESEED_INTERVAL_HOURS=6`): wipes all tables and reloads demo data so reviewer/demo mutations do not accumulate forever
- **Manual reset**: set `RESET_DB_ON_START=1`, or delete the DB file / volume

```bash
# wipe Docker volume and re-seed
docker compose down -v
docker compose up -d --build
```

Check schedule:

```bash
curl -s http://localhost:8834/ | jq .reseed
```

---

## What it implements

All routes the Android client calls (see `NessusApi.kt`):

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/server/status` | Connection test (`status: ready`) |
| GET/POST | `/scans` | List / create scans |
| GET | `/scans/{id}` | Scan detail, hosts, vulns, history, remediations |
| GET | `/scans/{id}/hosts/{hostId}` | Host-level findings |
| POST | `/scans/{id}/launch\|stop\|pause\|resume` | Scan state |
| PUT/DELETE | `/scans/{id}` | Rename / delete |
| POST/GET | `/scans/{id}/export…` | Export + status + PDF/CSV/HTML download |
| GET | `/plugins/plugin/{id}` | Plugin attributes |
| GET | `/editor/scan/templates` | Create-scan templates |
| GET/POST/DELETE | `/groups` | User groups |
| GET | `/scanners` | Scanner list (`id` `1` and `null` included) |
| * | `/scanners/{id}/agent-groups…` | Agent groups + membership |
| GET/DELETE | `/scanners/{id}/agents…` | Agents / unlink |

Auth header (same as the app):

```http
X-ApiKeys: accessKey=demo-access-key-reviewer; secretKey=demo-secret-key-reviewer
```

---

## Quick start (Docker Compose)

```bash
cd nessus_mock
cp .env.example .env
# edit keys / ports if you want
docker compose up -d --build
```

Health check:

```bash
curl -s http://localhost:8834/server/status
# {"status":"ready","progress":"100"}
```

Authenticated example:

```bash
curl -s -H "X-ApiKeys: accessKey=demo-access-key-reviewer; secretKey=demo-secret-key-reviewer" \
  http://localhost:8834/scans | head
```

Stop:

```bash
docker compose down
```

---

## Local run (no Docker)

```bash
cd nessus_mock
python -m venv .venv
# Windows: .venv\Scripts\activate
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
python app.py
```

---

## Android app / Play review settings

Configure **CyberAsk Scanner → Settings**:

| Field | Value |
|-------|--------|
| **API URL** | `http://YOUR_PUBLIC_HOST:8834` (or `https://…` if you put TLS in front) |
| **Access Key** | `demo-access-key-reviewer` (or your `.env` value) |
| **Secret Key** | `demo-secret-key-reviewer` (or your `.env` value) |
| **Scanner ID** | `1` (or pick from the in-app list after connect) |

### Play Console notes

- Provide the **public base URL**, access key, and secret key only to Google as **demo credentials**.
- Data is **synthetic** and resets when the container restarts (in-memory store).
- The mock does **not** scan real networks.

### TLS / cleartext

The Android app prefers **HTTPS** and may block plain HTTP depending on network security config. For review:

1. Prefer a reverse proxy (Caddy/nginx) with Let’s Encrypt in front of this container, **or**
2. Use a tunnel (Cloudflare Tunnel, ngrok) that terminates HTTPS.

---

## `.env` knobs

| Variable | Default | Meaning |
|----------|---------|---------|
| `PORT` | `8834` | Listen port |
| `DATABASE_PATH` | `./data/nessus_mock.db` | SQLite file path |
| `RESET_DB_ON_START` | `0` | Wipe DB and re-seed on startup |
| `RESEED_INTERVAL_HOURS` | `6` | Auto wipe+reseed interval (`0` disables) |
| `NESSUS_ACCESS_KEY` | `demo-access-key-reviewer` | Access key |
| `NESSUS_SECRET_KEY` | `demo-secret-key-reviewer` | Secret key |
| `NUM_SCANS` | `10` | Demo scans |
| `NUM_GROUPS` | `10` | User groups |
| `NUM_AGENT_GROUPS` | `10` | Agent groups |
| `AGENTS_PER_GROUP` | `10` | Agents in each group |
| `NUM_UNLINKED_AGENTS` | `15` | Extra agents not in a group |
| `NUM_HOSTS_PER_SCAN` | `8` | Hosts per scan |
| `NUM_VULNS_PER_HOST` | `12` | Findings per host |
| `NUM_HISTORY_PER_SCAN` | `3` | History entries |
| `NUM_SCANNERS` | `3` | Scanner entries |
| `NUM_TEMPLATES` | `8` | Scan templates |
| `EXPORT_READY_SECONDS` | `2` | Delay before export `ready` |

---

## Demo data highlights

- Mix of scan statuses: completed, running, paused, empty, canceled  
- Hosts with severity counts; plugin detail attributes for findings  
- Agent groups with agents; spare unlinked agents  
- PDF (and CSV/HTML/nessus) report generation for export  
- Start / stop / pause / resume update scan status in memory  

---

## Security

- **Demo only** — not a real vulnerability scanner  
- Change default keys if the instance is internet-facing  
- Do not point this at production data; there is none by design  
