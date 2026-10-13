#!/usr/bin/env bash
# Deploys the whole environment (legacy Case API + your app) with docker compose and checks that your app answers on :8080.
set -euo pipefail
cd "$(dirname "$0")"
# Login tokens need a secret signing key; make a random one once and keep it out of git (.env is ignored).
if ! grep -q '^JWT_SECRET=' .env 2>/dev/null; then
  echo "JWT_SECRET=$(openssl rand -hex 32)" >> .env
fi
docker compose up -d --build
echo "Waiting for your app on http://localhost:8080 ..."
for i in $(seq 1 60); do
  if curl -fsS -o /dev/null http://localhost:8080/ 2>/dev/null; then
    echo "Deployed. Open http://localhost:8080"; exit 0
  fi
  sleep 2
done
echo "Your app did not answer on http://localhost:8080 within 120 s. Check: docker compose logs" >&2
exit 1
