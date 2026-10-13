# Plan

**Now:** plan done · **Next:** S1 skeleton · **Elapsed:** 0:29 (19:36; start 19:07, live by 20:37, end 22:07)

User: the reviewer. Stack: Kotlin + Spring Boot, PostgreSQL, one plain HTML/JS page. Tests: `./gradlew test`.
Each slice: tests first for logic → `./deploy.sh` → check on http://localhost:8080 → tick ✓ → commit.

| # | Slice (what the user can do after it) | Done when (check) | Min | ✓ |
|---|---|---|---|---|
| S1 | **Skeleton.** Open http://localhost:8080 and see the app's page | `./deploy.sh` passes; `/` answers 200; Postgres up with Flyway tables; no published DB port; secrets from env | 15 | |
| S2 | **Main workflow, ugly.** See pending cases sorted by time left to SLA, open one, decide with a reason code; the decision lands in the Case API | Import script loads `data/cases.json` via `POST /api/ingest/cases`; one case decided in the browser shows as decided in `GET /cases/{id}` on the Case API; test for SLA ordering | 25 | |
| S3 | **Trustworthy data.** Bad export records are visible as "data problem", not silently wrong | Tests per rule: duplicate ID (first wins), `14.10.2026` dates, missing/invalid scores; import prints accepted/updated/problems; re-run changes nothing | 15 | |
| S4 | **Safe decision.** Saving never creates two decisions and never loses one; every decision has a reason | Tests with fake Case API: 503 then OK = one decision (same idempotency key); 409 → case refreshed + message; 422 shown; empty reason/comment rejected; decision log row only after API confirms | 20 | |
| S5 | **Take next.** Pick your name, press "Take next", get the most urgent case nobody else has; overdue and < 1 h marked | Test: two parallel claims never get the same case; simulated clock setting changes "time left" | 15 | |
| S6 | **Readable case.** Understand a case at a glance: scores, flags explained, earlier attempts by the same document | Each of the 8 flags has a plain-English text (marked as guidance, not policy); earlier attempts shown for a REPEAT_ATTEMPT case | 10 | |

Total 100 min → build done ~21:16. Main workflow live after S2 (~20:16), safe after S4 (~20:51).
After the build: use it myself + FRICTION.md fixes (~25 min) → `reviewer` and `security-reviewer` agents (~10 min) → `/wrap-up` (15 min).

**Parallel:** only S3 (ingest cleaning, backend) ∥ S6 (case view, frontend + flag texts), which touch different files. Default: sequential.

## Not now (from PRODUCT_NOTE.md)
- Team view for Lucia (next after the MVP); helpdesk lookup.
- Automated decisions or auto-approve rules.
- Login and permissions (reviewer picks their name).
- Replacing the back office; editing case data; images.
- The legacy system calling our ingest API (IT's work).
