-- Who is working on which case. One claim per case; the Case API cannot store this.
create table claim (
    case_id    text        primary key references verification_case (case_id),
    reviewer   text        not null,
    claimed_at timestamptz not null default now()
);

create index claim_reviewer_idx on claim (reviewer);
