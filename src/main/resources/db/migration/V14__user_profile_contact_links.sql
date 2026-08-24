-- =====================================================================
-- Wellnara — Flyway V14: provider contact links on the user profile
--
-- Optional public WhatsApp / Telegram links a provider may add to their
-- profile (at registration or later). Nullable; clients never set them.
--
-- Column type mirrors the entity mapping (@Column length = 500) so
-- ddl-auto=validate matches on prod.
-- =====================================================================

alter table user_profiles
    add column whatsapp_url varchar(500);

alter table user_profiles
    add column telegram_url varchar(500);
