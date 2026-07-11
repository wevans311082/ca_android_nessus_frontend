#!/usr/bin/env sh
# Quick API smoke test against a running mock instance.
# Usage: BASE_URL=http://localhost:8834 ./scripts/smoke_test.sh

set -e
BASE_URL="${BASE_URL:-http://localhost:8834}"
ACCESS="${NESSUS_ACCESS_KEY:-demo-access-key-reviewer}"
SECRET="${NESSUS_SECRET_KEY:-demo-secret-key-reviewer}"
AUTH="X-ApiKeys: accessKey=${ACCESS}; secretKey=${SECRET}"

echo "GET $BASE_URL/server/status"
curl -fsS "$BASE_URL/server/status"
echo
echo "GET /scans"
curl -fsS -H "$AUTH" "$BASE_URL/scans" | head -c 400
echo
echo "GET /scanners"
curl -fsS -H "$AUTH" "$BASE_URL/scanners"
echo
echo "OK"
