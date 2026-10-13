---
name: slice
description: Build one slice from PLAN.md end-to-end – tests, code, deploy, check, review, commit. Use as /slice S2.
disable-model-invocation: true
---
Build the slice named by the first word of $ARGUMENTS (e.g. S2) from PLAN.md, and only that slice.
Any text after the slice id is my extra instruction or decision: follow it and record it in DECISIONS.md with my reason.
1) Restate in 2 plain sentences what the user can do after this slice and how we will check it ("Done when" in PLAN.md).
   If the slice is unclear or bigger than ~25 minutes, stop and propose how to split it.
2) Update the PLAN.md status line to "Now: <slice id> (started HH:MM)".
3) Tests first for the logic, then the code. Run the tests (`./gradlew test`) and show the real result. Never skip or weaken a test.
4) Run ./deploy.sh and check the slice on the deployed app; show the request/URL and the response.
5) Use the reviewer agent on this slice's diff; fix every BLOCKER or MAJOR, re-run tests and deploy if you changed code.
   If the slice touched auth, input handling, data access or personal data, also use the security-reviewer agent.
6) Tick the slice in PLAN.md and update the status line (Now / Next / Elapsed). Add a DECISIONS.md row for any choice
   I should know about and explain it in one plain sentence.
7) Commit with a Conventional Commit (e.g. "feat(queue): sort pending cases by sla time left").
8) Report in plain English: what changed for the user, test and deploy result, review verdict, commit hash,
   and in 2–3 sentences how I can try it myself now. Then stop and wait.
If stuck after 3 attempts: stop changing code and tell me what you tried, the exact error, your top 3 causes
and the cheapest check to tell them apart.
