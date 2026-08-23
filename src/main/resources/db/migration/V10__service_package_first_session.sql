-- =====================================================================
-- Wellnara — Flyway V10: requested first session on a service package.
--
-- A client now buys a package and picks the time for its first session in one
-- request. The chosen start (UTC) is stored on the package until the provider
-- approves it, at which point the first appointment is scheduled from it. A
-- provider-granted package (offline) has no first session, so the column is
-- nullable and existing packages default to null.
-- =====================================================================

alter table service_packages
    add column first_session_start_utc timestamp;
