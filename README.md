# Build exercise: Onboarding review at Nordline Finance

You have 3 hours. Use Claude Code; it is already set up for you.

## Deliverables

1. **First 20 minutes:** a one-page product note in `PRODUCT_NOTE.md`: who the user is, what they are trying to do, what you will build, what you will deliberately not build, and what other approach you considered.
2. **Next ~2 hours:** build it, use it yourself, and improve it. Have the main workflow live and usable by the 90-minute mark. Deploy with `./deploy.sh` whenever you like.
3. **Last 15 minutes:** a one-page close in `CLOSE_NOTE.md`: what a user can do now, what you changed after trying it yourself, what is still rough, and what you would do with one more hour.

It does not need to be big. A small, clear product that helps someone is exactly what we are looking for.

## The situation

Nordline Finance (a fictional digital bank in Central Europe) opens accounts remotely. Customers scan their ID document and take a selfie. Automated checks (document authenticity, face match, liveness) approve most applications. Cases the checks are not sure about go to the **Onboarding Operations** team for manual review.

This message is from Lucia, Head of Onboarding Operations:

> Every morning we have 120–150 flagged cases waiting. The team works from the old back-office tool. It shows one case at a time as a wall of fields, in no particular order. People keep a spreadsheet on the side to know what to pick next. We regularly miss our SLA: a case must be decided within 4 hours, 8 hours for passports. Missed SLA means the customer usually gives up.
>
> New colleagues struggle the most. They don't know what the flags mean or which ones are serious, so they escalate too much or approve things they shouldn't. Last month compliance found decisions without a proper reason, and that is not allowed.
>
> I personally can't see how big the backlog is or who is overloaded until it's too late.
>
> Customers who call the hotline ask why they are still waiting, and the hotline has no idea.
>
> We can't replace the back-office system this year. But anything that helps the team get through the morning faster and safer would make a real difference.

## What is in the environment

| Path | What it is |
|---|---|
| `data/cases.json` | Export of today's verification cases (fictional people). Exported by a script that is known to be a bit sloppy |
| `data/reason_codes.json` | Reason codes compliance allows for a decision |
| `data/reviewers.json` | Reviewers and their shifts |
| `services/case-api/` | The **legacy back-office Case API** (already running in docker compose). Treat it as an external system you cannot change. Its docstring describes the endpoints |
| `docker-compose.yml` | Runs the Case API. Add your own service(s) there |
| `deploy.sh` | Builds and starts everything; your app must answer on port **8080** |

The Case API needs the header `X-Api-Key`. The key is configured in `docker-compose.yml` through the `CASE_API_KEY` environment variable.

Notes from the IT team about the legacy API: "It is slow sometimes, and saving a decision occasionally fails. People then click again and we are not sure what happens."

Images (document photos and selfies) are not available in this environment. Work with the check scores and flags.

## What we look at

- Who you decided the user is, and which workflow you chose to make better.
- The reasoning behind your decisions (product note, close note, repository history, your prompts).
- That the product actually works and someone can use it.
- Care with the data: these are identity documents of real customers in production.
