# Plan

User: **reviewer**. Lucia (Head of Onboarding Ops) and helpdesk are out of the MVP (see PRODUCT_NOTE.md).
Start 19:07 · workflow live by **20:37** (+1:30) · close note by **22:07** (+3:00).

Stack: Kotlin + Spring Boot (Java 21, Gradle wrapper), Spring Data JDBC, Flyway for tables, RestClient for the Case API; PostgreSQL 16; one plain HTML/JS page served by the app as a static file. Docker image built in a multi-stage Dockerfile.
Tests: JUnit 5 on the host: `./gradlew test` (Postgres via Testcontainers, fake Case API via WireMock).
Each slice: tests first for the logic, `./deploy.sh`, check on http://localhost:8080, update this file, commit.

## Slices

| # | Slice | Done when | Target | Status |
|---|---|---|---|---|
| 0 | **Skeleton.** `app` + `db` (Postgres) services in docker compose; Spring Boot serves an empty page on :8080; Flyway creates the tables on start; secrets from env | `./deploy.sh` passes; `/` answers; Postgres has no published port | 19:45 | todo |
| 1 | **Ingest.** `POST /api/ingest/cases` (own API key, batch, upsert by case ID) with cleaning: duplicate IDs (first wins), both date formats, missing/invalid scores stored as missing, a "data problem" note per issue; never overwrites a decision unless the push carries one. `scripts/import-example.sh` (curl + jq, both on the machine) sends `data/cases.json` through it; reason codes and reviewers loaded from JSON | Unit tests for each cleaning rule; import prints accepted/updated/problems; re-run changes nothing | 20:00 | todo |
| 2 | **Queue + take next.** Reviewer picks their name. Pending cases sorted by time left to SLA (4 h ID card / 8 h passport), using a simulated clock (default 2026-10-14 08:00 UTC, one setting); overdue and < 1 h marked. "Take next" claims the most urgent unclaimed case atomically (two reviewers can never get the same one) | Tests: SLA math, ordering, data-problem cases still shown, concurrent claim | 20:15 | todo |
| 3 | **Case view + decision.** Scores shown as good/borderline/bad; each flag explained in plain English with how serious it usually is; earlier attempts by the same document number. Decision form: decision + reason code + comment required. Sent to the Case API with one idempotency key per decision, retried on 503 with the same key; 409 → refresh case and tell the reviewer. Saved to our DB only after the API confirms. Decision log (who, what, reason, when, API result) | Tests with a fake Case API: 503 then OK = one decision; 409 path; 422 path; missing reason rejected. One end-to-end check against the real Case API | **20:37 live** | todo |
| 4 | ~~Team view for Lucia~~ moved after the MVP (decision 19:34) | – | – | dropped |
| 5 | **Use it myself.** Work ~10 cases as a reviewer; log friction in FRICTION.md; fix the top items. Run `user-tester` agent | FRICTION.md has before -> after entries | 21:35 | todo |
| 6 | **Review + harden.** `reviewer` and `security-reviewer` agents on the whole branch; fix blockers | No open BLOCKER/CRITICAL | 21:50 | todo |
| 7 | **Wrap-up.** `/wrap-up`: tests green, deploy, CLOSE_NOTE.md | Close note committed | 22:07 | todo |

## Cut order if behind
1. Earlier attempts and score colouring in slice 3 drop to plain values.
2. Slice 5 fixes limited to the top 1–2 items.
Never cut: reason code required, safe retry (idempotency key), tests for those.

## Known gaps (accepted for the MVP)
- No login: the reviewer picks their name. Anyone on the machine could act as any reviewer.
- Data is only as fresh as the last ingest; the legacy system does not call our endpoint yet.
- Flag explanations are our wording, not compliance-approved policy.
