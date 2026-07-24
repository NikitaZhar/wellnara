-- =====================================================================
-- Wellnara — Flyway V5 (step 3.7): concurrency integrity.
--
-- 1. Optimistic locking on appointments (@Version) — Wallet already has one
--    since V2. Two concurrent terminal transitions on the same appointment then
--    conflict; exactly one wins.
-- 2. Partial unique indexes as the database's last word (PostgreSQL):
--    - at most one ACTIVE appointment per provider + start time (double-booking);
--    - at most one FINAL wallet entry per appointment (double settlement/release).
--
-- Application-level checks (validateNoConflicts, in-transaction idempotency)
-- remain the fast path; these constraints are the backstop under real races.
--
-- ddl-auto=validate checks the version column; it does not check indexes.
-- =====================================================================

alter table appointments
    add column version bigint not null default 0;

create unique index uk_appointments_active_slot
    on appointments (provider_id, start_date_time_utc)
    where status in ('REQUESTED', 'SCHEDULED');

create unique index uk_wallet_entries_final_per_appointment
    on wallet_entries (appointment_id)
    where type in ('SETTLE', 'RELEASE', 'PACKAGE_CONSUME', 'PACKAGE_RELEASE');
