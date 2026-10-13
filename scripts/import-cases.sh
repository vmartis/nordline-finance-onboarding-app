#!/usr/bin/env bash
# Stands in for the legacy system: pushes data/cases.json through the app's ingest API and prints the summary.
set -euo pipefail
cd "$(dirname "$0")/.."
curl -sS --fail-with-body -X POST http://localhost:8080/api/ingest/cases \
  -H "Content-Type: application/json" \
  -H "X-Api-Key: ${INGEST_API_KEY:-local-dev-ingest-key-change-me}" \
  --data-binary @data/cases.json
echo
