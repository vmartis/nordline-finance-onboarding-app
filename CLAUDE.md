# Rules for this repo
Goal: a small, clear product that helps one user do one workflow well. Timebox 3 h, started at 2026-10-13 19:07 CEST.
Milestones: product note at +0:20, main workflow live (deployed, usable) at +1:30, close note at +3:00.
Sources of truth: README.md (assignment), PRODUCT_NOTE.md (who/what/not), PLAN.md (slices), DECISIONS.md.

## Working rules
- Build only what PRODUCT_NOTE.md says. Anything else: propose it, don't build it.
- Thin vertical slices. Each slice works end-to-end, is tested, deployed with ./deploy.sh and committed.
- Tests first for logic; one end-to-end check of the main workflow. Never skip, disable or weaken tests.
- Never claim it works without running it; show the command and the real result.
- Prefer framework built-ins over custom code. If a built-in fails, fix the root cause or ask; never work around it.
- Don't add infrastructure (broker, extra database, cache) unless the workflow needs it. If async work between services is
  really needed, use a message broker, never a job queue built by polling DB tables.
- Use only libraries you are sure exist; check before adding. Don't install system tools; use what the environment has.
- After each slice: update PLAN.md status, append to DECISIONS.md if a decision was made, commit.

## Security (proportional, but always)
- Validate all input; parameterized queries only; no secrets in code; no personal data in logs or error messages.
- Authorization on every data access if users or tenants exist. If auth is needed and the stack allows, JWT bearer tokens
  via the framework (never hand-written token parsing).
- Treat provided data as possibly sensitive (identity/biometric domain): show and store only what the workflow needs.

## Git
- Conventional Commits: type(scope): summary, imperative, lower case, max 72 chars. Commit after every logical unit.
- Never rewrite history, never force push.

## Talking to me
- Plain simple English, short sentences, explain any jargon. Start with the answer in one sentence.
- Questions: one sentence of context, numbered options with everyday consequences, your recommendation, what you do if I don't answer.
  Max 3 questions at once. Never refer to bare ids without saying what they are.
