# Hidden evaluation – Nordline onboarding review (read only AFTER you finish)

## The brief hides four possible users. A strong candidate picks ONE

| User | Workflow to improve | Notes |
|---|---|---|
| **Reviewer (Onboarding Ops)** | Morning queue: what to pick next and how to decide quickly and correctly | The most direct value; the best fit for "get through the morning faster and safer" |
| New reviewer | Understand flags and decide correctly | Can be part of the reviewer workflow (flag explanations, guidance) |
| Lucia (team lead) | Backlog and SLA risk, workload per reviewer | Valid choice, but reviewers keep suffering |
| Hotline agent | "Why is my application waiting?" | Valid but narrow; needs data minimisation (the hotline must not see scores and document numbers) |

The product note should say who, why this one, and what was left out. Considering the team-lead dashboard as the "other approach" is a good sign.

## What a good MVP looks like (reviewer workbench)

- **Prioritised queue:** pending cases sorted by SLA time left (4 h, or 8 h for passports), with SLA risk visible (for example "due in 25 min"), and filters (mine, unassigned, flag).
- **Case view in plain language:** flags explained in human terms, scores shown with meaning ("face match low: 0.52"), and repeat attempts by the same person linked.
- **Decision with mandatory reason code** from `/reason-codes`, written through the Case API.
- **Robust save:** retries on 503 with the **same `Idempotency-Key`**, so a double click or a retry never produces two decisions. A 409 is shown as "already decided by someone else".
- **Data minimisation:** document number masked by default (for example `SK****4865`), and date of birth shown as age or masked. Nothing personal in logs.
- The API key comes from env, never from frontend code. The browser talks only to your backend, not to the Case API directly.

## Data traps in `cases.json` (a strong candidate notices at least 2)

- [ ] About 4 % of `createdAt` values (7 cases) are in `dd.MM.yyyy HH:mm` format instead of ISO, which breaks SLA sorting if ignored.
- [ ] 2 exact duplicate cases (export glitch).
- [ ] `faceMatch` is `null` when the face was not captured (`FACE_NOT_CAPTURED`). Code that treats null as 0 or crashes is a bug.
- [ ] Repeat attempts by the same person (same `documentNumber`, `attempt` > 1, flag `REPEAT_ATTEMPT`). Relevant for fraud.
- [ ] Some cases are already decided (`APPROVED`/`REJECTED`) and must not be in the queue.
- [ ] Passports (AT) have an 8 h SLA, not 4 h.
- [ ] The Case API pages with a maximum of 50 items, so 135 pending cases need 3 calls.

## Legacy API behaviour (a strong candidate handles it and says so in the close note)

- [ ] Around 10 % of calls are slow (1.5 s). The UI shows loading state, or the backend caches the list.
- [ ] Around 7 % of POST `/decision` calls return 503. The product retries with the same `Idempotency-Key` and never creates a double decision.
- [ ] 409 when the case is already decided (a colleague was faster).
- [ ] 422 without a valid reason code. The UI should prevent this before sending.

## "Used it myself" evidence (close note)

Good examples of before → after:

- "I had to scroll to find the SLA; I put it first and coloured cases due in under 30 min."
- "The flag names meant nothing to me; I added one-line explanations."
- "A double click sent two requests; I added the idempotency key and disabled the button while saving."

## Red flags

- Builds for all four users and finishes none.
- Shows raw JSON or the full document number by default.
- The API key is in frontend code or in the repo outside docker-compose env.
- Ignores 503s, or retries without an idempotency key.
- The decision can be saved without a reason code.
- The product note is written by AI without a real decision ("users want a better experience").
- The close note claims things the deployed app doesn't do.

## Follow-up questions to prepare for

1. Why the reviewer and not Lucia?
2. What happens when two reviewers open the same case?
3. Which personal data does the reviewer really need to see, and why?
4. You had one more hour: why that and not something else?
5. Show us one moment where Claude was wrong and how you noticed.
