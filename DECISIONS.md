# Decisions
| Time | Decision | Options considered | Why | Decided by |
|---|---|---|---|---|
| 19:15 | Users: reviewer (main) and Lucia (second); helpdesk after the MVP | Reviewer, new reviewer, Lucia, helpdesk, compliance | The reviewer's morning is where SLA is lost; Lucia's view reuses the same data cheaply; helpdesk needs separate care with data access | Human |
| 19:18 | Own PostgreSQL database, filled through an ingest API; import script stands in for the legacy system | Memory only; SQLite; Postgres | Claims and decision log need storage; ingest API is the production path | Human |
| 19:18 | Decisions go to the Case API first, our DB only after it confirms | Write DB first, sync later | The legacy system stays the official record; no two versions of a decision | Human |
| 19:20 | Simulated clock, default 2026-10-14 08:00 UTC | Real clock | Data is dated tomorrow; real clock would make every case look on time | Human (default) |
| 19:26 | Python + FastAPI + psycopg 3 + httpx, plain HTML/JS page | Kotlin/Spring; Node | Fastest to build in the time; matches the Python Case API | Claude (stack left open) |
| 19:26 | `data/cases.json` not committed to git | Commit the whole environment | Identity documents of customers must not land in repository history | Claude |
