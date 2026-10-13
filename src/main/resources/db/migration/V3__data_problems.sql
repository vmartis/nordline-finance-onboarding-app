-- Plain-English notes on what was wrong in the export for this case. Never contains personal data.
alter table verification_case add column data_problems text[] not null default '{}';
