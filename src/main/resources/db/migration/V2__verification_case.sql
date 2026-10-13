-- Cases pushed by the legacy system through POST /api/ingest/cases. Personal data: only what the workflow needs.
create table verification_case (
    case_id         text primary key,
    status          text        not null,
    created_at      timestamptz,             -- null = unknown (unparseable in the export)
    sla_hours       int         not null,
    full_name       text,
    document_type   text,
    document_number text,
    date_of_birth   date,
    attempt         int         not null,
    flags           text[]      not null default '{}',
    score_document  numeric,                 -- null = missing, never 0
    score_face      numeric,
    score_liveness  numeric,
    notes           text,
    ingested_at     timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);

create index verification_case_status_idx on verification_case (status);
