-- One row per decision the Case API confirmed: who, what, reason, when, and the API's answer.
-- The free-text comment stays in the Case API (data minimisation).
create table decision_log (
    id              bigint generated always as identity primary key,
    case_id         text        not null references verification_case (case_id),
    reviewer        text        not null,
    decision        text        not null,
    reason_code     text        not null,
    idempotency_key uuid        not null unique,
    api_status      text        not null,
    decided_at      timestamptz not null default now()
);
