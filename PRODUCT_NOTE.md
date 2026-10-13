# Product note: Morning Queue for Onboarding Operations

## Who the user is
An **Onboarding Operations reviewer** working through the morning backlog of 120–150 flagged cases. They sit in the old back-office tool, keep a spreadsheet on the side to decide what to pick next, and new colleagues don't know which flags are serious. The second user is **Lucia**, the head of the team, who needs to see how big the backlog is and who is overloaded before deadlines are missed. The third user is **helpdesk (hotline) staff**, who need to look up an application when a customer calls and tell them where it stands (waiting, in review, decided, retake requested). They are **not part of the MVP**; see below.

## What they are trying to do
Decide every case **correctly, with a valid reason, and before its SLA runs out** (4 h for ID cards, 8 h for passports). Today they lose time choosing the next case, reading a wall of fields, and clicking "save" again when the legacy system fails, which leaves them unsure what was actually saved.

## What I will build
A small web app on top of the legacy Case API (it does not replace it):

1. **Queue ordered by deadline.** All pending cases, sorted by time left until SLA, with overdue and "due within 1 h" clearly marked. A **"Take next"** button gives the reviewer the most urgent case nobody else has taken. Claims are stored in the app's database, because the API cannot store them.
2. **Readable case view.** The check scores shown as clear good/borderline/bad values, each flag explained in plain English with how serious it usually is, and earlier attempts by the same applicant (same document number) shown together.
3. **Safe decision.** The decision requires a reason code from the compliance list and a short comment, so no decision is saved without a reason. Each decision gets one idempotency key, and retries after a 503 reuse it, so double clicks and retries never create two decisions. If the case was already decided (409), the app says so clearly.
4. **Team view for Lucia.** Pending cases grouped by time left (overdue, <1 h, <2 h, later), plus open and decided cases per reviewer against their shift.

**Own database, fed by an ingest API.** The app has its own database (PostgreSQL, running as its own service in docker compose with data in a Docker volume). In production the **legacy system pushes data to us**: the app exposes an ingest endpoint (`POST /api/ingest/cases`, a batch of cases), protected by its own API key, and the legacy system calls it whenever cases are created or their status changes. Ingest is an upsert by case ID, so the same data can be sent again safely. In this environment we can't change the legacy system, so an **import script stands in for it**: it reads `data/cases.json` and sends it through the same ingest endpoint, which tests the real path. Reason codes and reviewers are loaded once from their JSON files. Ingest cleans the sloppy export without guessing. Duplicate case IDs keep the first record, as the API does. Broken dates are parsed or marked. Missing or invalid scores are stored as "missing", never as 0. Every problem is saved as a visible "data problem" note on the case, and the endpoint returns a summary (accepted, updated, problems), which the script prints. A pushed record never overwrites a decision already made unless the push itself carries a decided status. The database also holds claims and a decision log (who, what, reason, when, result from the API).

**Decisions still go to the Case API first.** The legacy system stays the official record. A decision is written to the database only after the API confirms it. If the API answers 409 (already decided, e.g. in the old tool), the app updates the case from the API and tells the reviewer.

Data care: the API key stays on the server. The database holds personal data, so Postgres is reachable only inside the docker compose network (no published port), its data lives in a Docker volume on this machine, and nothing from it is committed to git. Its password comes from an environment variable, like the API keys. Only fields the workflow needs are imported. Document numbers and dates of birth are shown only on the opened case and are never written to logs.

"Now" is a **simulated clock** (default: 2026-10-14 morning) because the data is dated tomorrow. It can be changed in one setting.

Stack: a small Python backend serving one simple web page, plus a PostgreSQL service, both added to `docker-compose.yml`; the app listens on port 8080 and creates its tables on startup. The import script runs once the app is up and sends the example data through the ingest endpoint.

## What I will deliberately not build
- **Automated decisions or auto-approve rules.** The tool helps people decide; it does not decide for them.
- **Login or permissions.** The reviewer picks their name from a list. Real access control belongs to the bank's systems.
- **Helpdesk lookup (next step after the MVP).** Helpdesk staff would search for an application (e.g. by case ID or name plus date of birth) and see only its progress: status, time waiting, and the expected decision time from the SLA. They would not see scores, flags or document numbers. It is left out of the MVP because the reviewers' morning is the most urgent problem, and showing customer data to another team needs its own care around access and what to show. The same queue data makes it a small addition later.
- **A replacement for the back office.** No editing of case data and no images (not available).
- **The legacy side of the integration.** We define and build the ingest endpoint, but making the legacy system call it is IT's work. Until then, the data is only as fresh as the last import, and a case decided in the old tool is noticed only when the Case API returns 409.

## Other approach considered
**A Lucia-first dashboard** (backlog and workload only). It's easier to build and answers the "too late" problem, but it only shows the pain without reducing it. The cases still get worked in the wrong order, and new reviewers still don't understand the flags. I chose the reviewer's queue and decision flow as the core, with the dashboard as a cheap add-on built from the same data.
