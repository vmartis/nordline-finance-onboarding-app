# Evaluation against the hidden criteria

Checked on 2026-10-14 against the code, the docs and the running app (http://localhost:8080 answers 200), at commit 57ca646.

**Result: about 85 %.** The app covers most of the criteria. The biggest loss is that the case panel shows the full document number and date of birth, which the criteria list as a red flag.

| Area | Weight | Score | Why |
|---|---|---|---|
| One user, a clear reason, the others left out | 10 | 10 | The product note picks the reviewer, leaves out Lucia (the team lead) and the hotline, and names "Lucia-first dashboard" as the other approach |
| What the main product does | 30 | 26 | See below |
| Data traps noticed (7) | 15 | 14 | 6 of 7 handled; the 7th does not apply to our design |
| Behaviour of the legacy Case API (4) | 15 | 14 | All 4 handled; the close note describes them in user terms but does not name them plainly |
| "Used it myself" changes | 10 | 6 | 2 real before → after changes, but small ones |
| Red flags (points taken off) | 15 | 10 | Full document number shown by default |
| Ready for the follow-up talk | 5 | 5 | 13 decisions made by the human in DECISIONS.md, plus caught AI mistakes to show |
| **Total** | 100 | **85** | |

## What the main product does
- ✅ **Queue order:** sorted by time left before the deadline (4 h for ID cards, 8 h for passports). Time left is shown, and rows turn red under 1 h and amber under 2 h.
- ⚠️ **No filters** (mine / free / by flag). "Take next" and the "taken by" label only partly make up for that.
- ✅ **Case panel:** flags are explained in plain words; each score is shown with a label and its number; other attempts with the same document are linked.
- ✅ **Reason required:** a reason code and a comment are required, and the decision goes through the Case API.
- ✅ **Safe save:**
  - a failed save (503) is retried with the same Idempotency-Key, so there are never two decisions;
  - a 409 is shown as "already decided by X";
  - the Save button is disabled while saving.
- ❌ **Data minimisation:** the queue hides personal data, but the case panel shows the full document number and date of birth (`web/src/components/case-sheet.tsx:188-190`). The criteria want a masked number (e.g. `SK****4865`) and an age instead of the birth date.
- ✅ **Keys and logs:** API keys come only from env / `docker-compose.yml`. The browser talks only to our backend, and no personal data goes into logs.

## Data traps
Handled:
- the 7 dates in `dd.MM.yyyy HH:mm` format;
- the 2 duplicates;
- the null face match (`FACE_NOT_CAPTURED`);
- repeat attempts are linked;
- already decided cases are kept out of the queue;
- the 8 h passport deadline.

Not applicable: the Case API returns at most 50 cases per call. We load cases from the export through our ingest endpoint instead of listing them from the Case API.

## Legacy Case API behaviour
- **Slow calls:** loading placeholders and timeouts.
- **503:** retried with the same key.
- **409:** explained to the reviewer.
- **422:** prevented in the form before sending.

## Risks for the follow-up
- **"Used it myself":** the 2 changes are plain status words and an explanation when the login runs out. They are weaker than the criteria's examples. Flag texts and deadline colours were planned steps, not fixes made after trying the app.
- **Login:** reloading the page logs you out, which will show when we use the app together.
- **Scope:** login was left out in the product note but built later on the human's request. This is recorded in DECISIONS.md.

## Quickest gain
Mask the document number and show age instead of the birth date. That is about 10 minutes, removes the only red flag, and brings the score to about 92 %.
