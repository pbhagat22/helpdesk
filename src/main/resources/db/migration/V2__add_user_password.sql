-- Users log in with email + password. Only a one-way hash is stored, never the password.
-- NOT NULL means this fails on a database that already has users: wipe dev data first
-- (docker compose down -v). A production database would instead add the column as
-- nullable and make existing users set a password before tightening the constraint.
alter table users add column password_hash varchar(100) not null;
