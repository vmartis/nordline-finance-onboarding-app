-- Reviewer logins. Passwords are stored only as bcrypt hashes (pgcrypto makes them in scripts/add-users.sh).
create extension if not exists pgcrypto;

create table app_user (
    username      text        primary key,
    password_hash text        not null,
    created_at    timestamptz not null default now()
);
