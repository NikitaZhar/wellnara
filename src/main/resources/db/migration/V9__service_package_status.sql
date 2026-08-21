-- =====================================================================
-- Wellnara — Flyway V9: service package lifecycle status.
--
-- A client-requested package is held pending provider approval (REQUESTED),
-- then becomes ACTIVE (approved, sessions granted) or REJECTED (declined). A
-- provider-granted package is ACTIVE from the start. Existing packages predate
-- the request flow and were granted directly, so they default to ACTIVE.
-- =====================================================================

alter table service_packages
    add column status varchar(16) not null default 'ACTIVE';
