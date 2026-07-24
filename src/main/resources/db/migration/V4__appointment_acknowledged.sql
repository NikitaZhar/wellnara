-- =====================================================================
-- Wellnara — Flyway V4 (step 3.6): soft-dismiss for appointment notifications.
--
-- Once an appointment carries wallet ledger entries (a HOLD from step 3.5) it is
-- never deleted. Dismissing a notification now flips this flag instead of
-- deleting the row, so the appointment stays in history. Existing rows default
-- to not acknowledged.
-- =====================================================================

alter table appointments
    add column acknowledged boolean not null default false;
