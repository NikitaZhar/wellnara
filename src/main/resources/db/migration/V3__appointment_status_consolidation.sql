-- =====================================================================
-- Wellnara — Flyway V3 (step 3.2): consolidate appointment statuses,
-- remove payment, record cancellation initiator and time.
--
-- Status set goes from 8 to 5:
--   CONFIRMED, PAYMENT_REQUESTED            -> SCHEDULED
--   REJECTED, CANCELLED_BY_PROVIDER         -> CANCELLED (initiator PROVIDER)
--   CANCELLED_BY_CLIENT                     -> CANCELLED (initiator CLIENT)
--   REQUESTED, COMPLETED, NO_SHOW           unchanged
--
-- V1/V2 are not modified. Column types mirror Hibernate 6.5 / PostgreSQL so
-- that ddl-auto=validate keeps matching the entities.
-- =====================================================================

alter table appointments
    add column cancellation_initiator varchar(255);

alter table appointments
    add column cancelled_at timestamp(6);

-- Drop the old status CHECK before rewriting the values it forbids.
-- Postgres auto-named the inline column check from V1 'appointments_status_check'.
alter table appointments
    drop constraint appointments_status_check;

-- Record cancellation initiator and time from the retiring statuses.
update appointments
   set cancellation_initiator = 'PROVIDER',
       cancelled_at = updated_at
 where status in ('REJECTED', 'CANCELLED_BY_PROVIDER');

update appointments
   set cancellation_initiator = 'CLIENT',
       cancelled_at = updated_at
 where status = 'CANCELLED_BY_CLIENT';

-- Collapse the old statuses onto the new set.
update appointments
   set status = 'SCHEDULED'
 where status in ('CONFIRMED', 'PAYMENT_REQUESTED');

update appointments
   set status = 'CANCELLED'
 where status in ('REJECTED', 'CANCELLED_BY_PROVIDER', 'CANCELLED_BY_CLIENT');

alter table appointments
    add constraint appointments_status_check
    check (status in ('REQUESTED', 'SCHEDULED', 'CANCELLED', 'COMPLETED', 'NO_SHOW'));

alter table appointments
    add constraint appointments_cancellation_initiator_check
    check (cancellation_initiator in ('PROVIDER', 'CLIENT'));
