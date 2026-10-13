#!/usr/bin/env bash
# Adds every reviewer as a login user. Default password: 12345 (human's decision; override with DEFAULT_PASSWORD).
# Stored only as a bcrypt hash (pgcrypto, cost 10 like Spring's checker, so login timing doesn't reveal names). Existing users keep their password. Run after ./deploy.sh.
set -euo pipefail
cd "$(dirname "$0")/.."
docker compose exec -T db psql -U queue -d queue -v ON_ERROR_STOP=1 -q \
  -v pw="${DEFAULT_PASSWORD:-12345}" <<'SQL'
insert into app_user (username, password_hash)
select id, crypt(:'pw', gen_salt('bf', 10)) from reviewer
on conflict (username) do nothing;
select 'users: ' || string_agg(username, ', ' order by username) from app_user;
SQL
