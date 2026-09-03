-- =====================================================================
-- Wellnara — Flyway V18: provider-client link active flag
--
-- Marks whether a client has full (booking) access. An inactive client can
-- still sign in and view the provider's services, but cannot book appointments
-- or request packages. Existing links default to active.
-- =====================================================================

alter table provider_client_links
    add column active boolean not null default true;
