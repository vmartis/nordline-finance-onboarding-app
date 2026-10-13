# Plan

**Now:** N9 done (21:22) · **Next:** short friction pass, close note · **Elapsed:** 2:15 (21:22; start 19:07, live by 20:37, end 22:07)

User: the reviewer. Stack: Kotlin + Spring Boot, PostgreSQL, React SPA in `web/` (see CLAUDE.md "Frontend"). Tests: `./gradlew test`, `npm test` in `web/` (also run in the Docker build).
Each slice: tests first for logic → `./deploy.sh` → check on http://localhost:8080 → tick ✓ → commit.

| # | Slice (what the user can do after it) | Done when (check) | Min | ✓ |
|---|---|---|---|---|
| S1 | **Skeleton.** Open http://localhost:8080 and see the app's page | `./deploy.sh` passes; `/` answers 200; Postgres up with Flyway tables; no published DB port; secrets from env | 15 | ✓ |
| S2a | **See the queue.** Pending cases listed, most urgent first (time left to SLA) | Import script loads `data/cases.json` via `POST /api/ingest/cases` (API key) and prints accepted/updated; `/` lists pending cases by time left; test for SLA ordering | 20 | ✓ |
| S2b | **Decide a case.** Open a case, pick your name, decision and reason code; the decision lands in the Case API | One case decided in the browser shows as decided in `GET /cases/{id}` on the Case API; test with fake Case API for the happy path | 20 | ✓ |
| S3 | **Trustworthy data.** Bad export records are visible as "data problem", not silently wrong | Tests per rule: duplicate ID (first wins), `14.10.2026` dates, missing/invalid scores; import prints accepted/updated/problems; re-run changes nothing | 15 | ✓ |
| S4 | **Safe decision.** Saving never creates two decisions and never loses one; every decision has a reason | Tests with fake Case API: 503 then OK = one decision (same idempotency key); 409 → case refreshed + message; 422 shown; empty reason/comment rejected; decision log row only after API confirms | 20 | ✓ |
| S5 | **Take next.** Pick your name, press "Take next", get the most urgent case nobody else has; overdue and < 1 h marked | Test: two parallel claims never get the same case; simulated clock setting changes "time left" | 15 | ✓ |
| S6 | **Readable case.** Understand a case at a glance: scores, flags explained, earlier attempts by the same document | Each of the 8 flags has a plain-English text (marked as guidance, not policy); earlier attempts shown for a REPEAT_ATTEMPT case | 10 | ✓ |

Total 100 min → build done ~21:16. Main workflow live after S2 (~20:16), safe after S4 (~20:51).
After the build: use it myself + FRICTION.md fixes (~25 min) → `reviewer` and `security-reviewer` agents (~10 min) → `/wrap-up` (15 min).

**Parallel:** only S3 (ingest cleaning, backend) ∥ S6 (case view, frontend + flag texts), which touch different files. Default: sequential.

## Not now (from PRODUCT_NOTE.md)
- Team view for Lucia (next after the MVP); helpdesk lookup.
- Automated decisions or auto-approve rules.
- Login and permissions (reviewer picks their name).
- Replacing the back office; editing case data; images.
- The legacy system calling our ingest API (IT's work).

## If time allows (after MVP)
| Prio | # | Item (what the user gains) | Why not in MVP | Depends on | Touches (files/areas) | Min | ✓ |
|---|---|---|---|---|---|---|---|
| 1 | N0a | **Login, backend** (omitted in note – OK given by human at 20:52). Reviewers log in with name + password; the API answers only with a valid JWT bearer token, and every decision and claim is made as the logged-in user (no more picking a name). Users are added to the DB by a script; default password for each user is `12345` (human's decision) | Note left login to the bank's systems; human asked for it as the first optional step | MVP done | users table (Flyway), `scripts/add-users.sh`, Spring Security resource server + `POST /api/auth/login` issuing the JWT (framework built-ins), `/api/**` protected except ingest (own API key) and login; tests | 20 | ✓ |
| 2 | N0b | **Login, screen.** Login page; token kept in memory and sent with every call; logout; name shown in the header instead of the name picker | Needs N0a | N0a | `web/` login page, `api.ts` auth header, router guard | 15 | ✓ |
| 3 | N9 | **Deadline highlight** (human's request, 21:02). Whole queue rows highlighted: danger (red) when less than 1 h is left or overdue, warning (amber) when less than 2 h is left, so urgent cases stand out at a glance | MVP marks only the time badge (red overdue, orange < 1 h) | S5 | `web/src/lib/time-left.ts` (+ test for the 1 h / 2 h edges), `web/src/pages/queue-page.tsx` | 10 | ✓ |
| 4 | N1 | **Save & next.** After saving, the next most urgent free case opens by itself: one click less on each of 120–150 cases | MVP works without it; needs claims first | S4, S5 | `web/src/components/case-sheet.tsx`, claim endpoint | 10 | |
| 5 | N2 | **Backlog strip.** One line above the queue: "3 overdue · 12 due within 1 h · 118 later", so the reviewer sees the pressure at a glance | Queue already sorts by urgency; this is a summary on top | S5 (overdue/<1 h marks) | `web/src/pages/queue-page.tsx` (from `/api/queue`, no backend change) | 10 | ✓ |
| 6 | N3 | **My decisions today.** A list of what I saved and what the back office answered, so I never wonder "did that go through?" | Needs the decision log from S4 | S4 | new `GET /api/decisions?reviewer=`, small sheet/page in `web/` | 15 | |
| 7 | N4 | **Reason codes that fit the decision.** After "Approve", only approve-type reasons are offered, so new colleagues can't pick a contradicting reason | The mapping is a compliance rule; must be confirmed, shown as guidance | S2b | `web/src/lib/decision.ts`, `case-sheet.tsx` | 10 | |
| 8 | N5 | **Put a case back.** Release a taken case I can't finish, so it doesn't sit blocked until its SLA runs out | Rare in the first morning; claims come with S5 | S5 | claim endpoint + button in `case-sheet.tsx` | 10 | |
| 9 | N6 | **Queue stays fresh.** The queue reloads every 60 s and shows "updated 1 min ago", so cases taken by others disappear without F5 | Manual reload works for one reviewer | S5 | `web/src/pages/queue-page.tsx` (TanStack Query refetch interval) | 5 | |
| 10 | N7 | **Find a case by ID.** Type a case ID a colleague mentions and jump to it | The queue order covers the normal path | S2b | `web/src/pages/queue-page.tsx` | 10 | |
| 11 | N8 | **Lucia's workload view, minimal** (omitted in note – needs my OK). Open and decided cases per reviewer against their shift | Left out to serve one user well; became cheap once claims and the decision log exist | S4, S5 | new `GET /api/team`, new page in `web/` | 20 | |

An N-item starts only when the whole MVP table is ticked, there are no open fixes in FRICTION.md with higher value, and there are at least (item minutes + 10) minutes left before +2:35.
