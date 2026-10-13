---
name: wrap-up
description: Final 15–20 minutes – make the deployed state clean and true, then draft the close note.
disable-model-invocation: true
---
1) Start nothing new. Run all tests (`./gradlew test`); if something is red, show it and ask me (fix only if it takes < 5 min, otherwise revert that change with git revert).
2) Commit everything that works. Unfinished work goes to a separate branch, never into the main branch.
3) Run ./deploy.sh and check the main workflow once on the deployed app; show the result.
4) Update PLAN.md status: what is done, what is not.
5) Draft CLOSE_NOTE.md (one page) from FRICTION.md, DECISIONS.md, PLAN.md and git log with sections:
   What a user can do now · What I changed after trying it myself (before -> after) · What is still rough · With one more hour I would.
   Facts only; mark the draft "DRAFT – human to edit". Commit "docs: add close note draft".
