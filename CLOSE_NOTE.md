# Close note: Morning Queue for Onboarding Operations

> **DRAFT – human to edit.** Facts are taken from FRICTION.md, DECISIONS.md, PLAN.md and the git log (24 commits, 13 Oct 2026, 19:07–21:35).

## What a user can do now
An Onboarding Operations reviewer can work the morning backlog in one screen, at http://localhost:8080:
- **Log in** with their reviewer name (users added by `scripts/add-users.sh`, default password `12345`). Every claim and decision is made as the logged-in user.
- **See the queue by deadline.** All pending cases, most urgent first (4 h ID card, 8 h passport). A strip shows "x overdue · y due within 1 h · z later". Rows turn red under 1 h and amber under 2 h. Cases with problems in the export carry a "data problem" badge.
- **Press "Take next"** to get the most urgent case nobody else has taken. Two people pressing at once never get the same case.
- **Read a case at a glance.** Scores are shown as good / borderline / bad, and a score whose check raised a flag is never "good". Each flag has a plain-English explanation ("guidance, not policy"). Other attempts with the same document, data-problem notes and plain-language statuses are shown.
- **Decide safely.** A reason code and a comment are required. "Save & next" saves and opens the next case. The decision goes to the legacy Case API first, and our copy changes only after it confirms. One idempotency key per decision means retries after 503s and double clicks never save twice. "Already decided elsewhere" (409) refreshes the case and says by whom. Every confirmed decision is logged (who, what, reason, when, API answer).
- **Behind it:** an ingest API with its own key cleans the sloppy export without guessing. Duplicates keep the first record, zone-less dates are read and noted, and bad scores show as "missing", never 0. Running the import again changes nothing. Personal data appears only on the opened case and never in logs.

## What I changed after trying it myself (before -> after)
From the use-it-myself pass (FRICTION.md) and from deciding real cases in the browser during each slice:
- Case statuses: `PENDING_REVIEW` / `RECAPTURE_REQUESTED` codes -> "Waiting for review" / "Retake requested".
- Expired login: silently back to the login page -> "Your session ended. Log in again."
- Saving in the browser: the Case API saved the decision but crashed before replying, so the app said "nothing was saved" -> fixed the Case API's storage permission (compose only) and sent `Content-Length`; saves are confirmed.
- Score display: green "good" next to "Low face match – usually serious" (13 such cases) -> "flagged by the check". A score of 0.797 shown as "0.80 · borderline" -> 3 decimals.
- Decision form after reload: a hidden remembered name could be submitted -> the form waits for the list (later replaced by login).
- Backlog strip: red "0 overdue" (false alarm) -> grey when zero.
- Moving between cases: decide, close, go back to the queue, Take next -> "Save & next" in one click.

## What is still rough
- **Decision and reason code are not linked.** "Approve" + "Document invalid" is accepted. During testing one such record was created in the legacy system (VC-20261014-0053); it needs fixing in the back office.
- **Login is basic.** Every user starts with `12345` and there is no "change password". Reload logs you out (the token is kept only in memory). A ~30 ms login timing gap remains.
- **Claims never expire and cannot be released.** Anyone can still open and decide a case a colleague has taken, with no warning.
- **Guidance is ours, not compliance's:** flag texts, score bands (0.80 / 0.60) and reading zone-less export times as Central European time.
- **Open friction:** vague "Validation failure" errors; "Take next" does not say it is your own open case; odd attempt dates are not explained; the login hint names a real user.
- **Hardening:** no request-size limit on ingest; a save can take up to ~16 s in the worst case; the data is only as fresh as the last import.

## With one more hour I would
1. Link reason codes to decisions, using a mapping confirmed by compliance (backlog N4), and reject mismatches on the server (~30 min).
2. Add "change password at first login" (~15 min).
3. Show "Taken by X" in the case panel and let a reviewer put a case back (backlog N5) (~15 min).
4. If time is left: "My decisions today" (N3) and a request-size cap on ingest.
