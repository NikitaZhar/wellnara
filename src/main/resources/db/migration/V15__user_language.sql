-- =====================================================================
-- Wellnara — Flyway V15: preferred user language
--
-- Preferred UI and notification language as an ISO 639 code (e.g. 'ru',
-- 'en'), captured from the active locale at registration. Nullable: NULL
-- means the user has no stored preference and the application falls back to
-- the default language (ru).
--
-- Column type mirrors the entity mapping (@Column length = 8) so
-- ddl-auto=validate matches on prod.
-- =====================================================================

alter table users
    add column language varchar(8);
