-- Reference data loaded once from data/reviewers.json and data/reason_codes.json.
create table reviewer (
    id    text primary key,
    team  text not null,
    shift text not null
);

create table reason_code (
    code  text primary key,
    label text not null
);
