# Decisions
| Time | Decision | Options considered | Why | Decided by |
|---|---|---|---|---|
| 19:15 | (changed at 19:34) Users: reviewer (main) and Lucia (second); helpdesk after the MVP | Reviewer, new reviewer, Lucia, helpdesk, compliance | The reviewer's morning is where SLA is lost; Lucia's view reuses the same data cheaply; helpdesk needs separate care with data access | Human |
| 19:18 | Own PostgreSQL database, filled through an ingest API; import script stands in for the legacy system | Memory only; SQLite; Postgres | Claims and decision log need storage; ingest API is the production path | Human |
| 19:18 | Decisions go to the Case API first, our DB only after it confirms | Write DB first, sync later | The legacy system stays the official record; no two versions of a decision | Human |
| 19:20 | Simulated clock, default 2026-10-14 08:00 UTC | Real clock | Data is dated tomorrow; real clock would make every case look on time | Human (default) |
| 19:26 | (superseded at 19:31) Python + FastAPI + psycopg 3 + httpx, plain HTML/JS page | Kotlin/Spring; Node | Fastest to build in the time; matches the Python Case API | Claude (stack left open) |
| 19:26 | `data/cases.json` not committed to git | Commit the whole environment | Identity documents of customers must not land in repository history | Claude |
| 19:31 | Kotlin + Spring Boot, Spring Data JDBC, Flyway, RestClient; tests with JUnit, Testcontainers, WireMock | Python + FastAPI (chosen at 19:26) | Human's own default when the stack is open; Java 21, Kotlin, Gradle are installed; Spring has built-ins for validation, DB access and HTTP | Human |
| 19:34 | MVP serves only the reviewer; Lucia's team view moves after the MVP, with helpdesk | Reviewer + Lucia in the MVP | One primary user and one main workflow; more time for the reviewer flow | Human |
| 19:52 | DB password from `DB_PASSWORD` env, with a local-only default like the given `CASE_API_KEY` | Required `.env` file; hard-coded | `./deploy.sh` works out of the box on this machine; a shared host must set it (switch to `:?` then) | Claude |
| 19:52 | Spring Boot 4.1.1, Kotlin 2.4.20, Postgres 17; Gradle project at repo root, `.dockerignore` allowlist keeps `data/` out of the image | Separate `app/` folder | Plain `./gradlew test` from the root; allowlist makes leaking customer data into the image impossible by default | Claude |
| 19:52 | Applied Flyway migrations are never edited; changes go in a new migration | Edit V1 | Editing V1 broke startup (checksum mismatch) during S1 | Claude |
| 19:54 | Split S2 into S2a (see the queue) and S2b (decide a case) | One S2 of ~45 min | Each half is deployed and committed alone, so the queue is live even if deciding runs late | Human |
| 20:01 | Ingest checks its own key in `X-Api-Key` with a small Spring interceptor (constant-time compare; no key set = every call refused) | Spring Security; JWT | System-to-system call with one shared key, as the product note says; JWT is for users, and we have no login | Claude |
| 20:01 | Cases with an unknown creation time go to the top of the queue, marked "unknown (data problem)" | Hide them; put them last | An unknown deadline may already be overdue; hiding it would lose the case | Claude |
| 20:01 | A pushed "pending" record never reopens a decided case; a pushed decided status is taken | Last push always wins | Product note rule; found by the reviewer before S2b could re-open decisions on re-import | Claude |
| 20:01 | Birth date comes in as text and is checked by pattern; Spring's client-error warnings are turned off | Parse as a date type | A parse error would write the raw birth date into the log (found by the security reviewer) | Claude |
| 20:01 | Kotlin flag `-Xemit-jvm-type-annotations` | Validate the list by hand | Without it Kotlin drops `@Valid` inside `List<...>` and Spring's built-in validation never runs | Claude |
