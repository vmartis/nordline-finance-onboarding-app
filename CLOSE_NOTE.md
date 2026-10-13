# WHAT A USER CAN DO NOW
I built this for an Onboarding Operations reviewer working the morning backlog at http://localhost:8080. Every item below was checked on the deployed app.
- **Log in** with your reviewer name and password (users come from `scripts/add-users.sh`; the default password is `12345`). Your name shows in the header, and everything you claim or decide is saved under it.
- **See what is most urgent.** All pending cases are listed with the shortest time to their deadline first (4 h for ID cards, 8 h for passports). One line on top counts overdue cases, cases due within 1 h, and the rest. Rows turn red under 1 h and amber under 2 h. Cases with a problem in the export carry a "data problem" badge that explains what was wrong.
- **Press "Take next"** to get the most urgent case nobody else has taken. If you press it again, you get your own open case back. Cases taken by colleagues show "taken by …".
- **Understand a case quickly.** Each score is shown as good, borderline or bad. A score whose own check raised a flag is never shown as good. Each flag is explained in plain words, with how serious it usually is ("guidance, not policy"). Other attempts with the same document are listed, and you can click through to them.
- **Decide with a reason.** A reason code and a comment are required. "Save & next" saves and opens your next case, and "Save & close" returns to the queue.
- **Trust the save.** The decision goes to the back office first, and pressing Save twice or a failing back office never creates two decisions. If someone already decided the case elsewhere, you are told who did, and nothing new is saved.
- **Trust the data.** Duplicate records, unreadable times and bad scores are shown as data problems, never silently wrong. Importing the same data again changes nothing.

# WHAT I CHANGED AFTER TRYING IT MYSELF
- **Statuses: codes -> plain words.** Before, the case panel and the save message showed `PENDING_REVIEW` or `RECAPTURE_REQUESTED`. Now they say "Waiting for review" or "Retake requested". This matters because new colleagues should not have to learn system codes to know where a case stands.
- **Expired login: silent -> explained.** Before, when a login ran out, the reviewer was dropped on the login page with no reason. Now the page says "Your session ended. Log in again to continue." This matters because it avoids the "did my last click save?" doubt in the middle of a busy morning.

# WHAT IS STILL ROUGH
- **Decision and reason code are not linked.** "Approve" with "Document invalid" is accepted. One such record was created on VC-20261014-0053 during testing and needs fixing in the back office.
- **Open friction (FRICTION.md):**
  - Some errors say only "Validation failure".
  - "Take next" does not say the case it gives back is your own open case.
  - There is no warning in the case panel when a colleague has taken the case.
  - Attempt dates that look out of order are not explained.
  - The login hint names a real user.
- **Login is basic.** Every user starts with `12345` and there is no "change password". Reloading the page logs you out (the token is kept in memory only, a trade-off we accepted). Login answers about 30 ms faster for unknown names.
- **Claims never expire, and a case cannot be put back.** Anyone can still decide a case someone else took.
- **Accepted review findings:**
  - A save can take up to about 16 s when the back office is slow.
  - If you close the panel while the next case loads, it opens anyway.
  - Amber means both "1–2 h left" and "data problem".
- **Guidance not confirmed by compliance:** the flag texts, the score bands (0.80 / 0.60), and reading export times without a time zone as Central European time.
- **Hardening:**
  - The import has no request-size limit.
  - Local default keys for the import and the Case API are set in `docker-compose.yml`.
  - Data is only as fresh as the last import.
- **Nothing half-done:** all work is on `main`, and there are no other branches.

# WHAT IT COULD DO NEXT (WITH ONE MORE HOUR)
All MVP slices are done. Next come the open "If time allows" items, in PLAN.md order:
1. **My decisions today:** see what I saved and what the back office answered. 15 min. PLAN.md – N3
2. **Reason codes that fit the decision:** after "Approve", only approve-type reasons are offered. The mapping needs compliance's OK. 10 min. PLAN.md – N4
3. **Put a case back:** release a case I can't finish. 10 min. PLAN.md – N5
4. **Queue stays fresh:** reloads every 60 s. 5 min. PLAN.md – N6
5. **Find a case by ID.** 10 min. PLAN.md – N7
6. **Lucia's workload view,** omitted in the product note and needs an OK. 20 min. PLAN.md – N8

N3 to N7 fit into one hour (50 min plus checks). N8 does not, and it needs an OK first.
