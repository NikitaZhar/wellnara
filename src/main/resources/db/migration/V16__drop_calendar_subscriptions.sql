-- =====================================================================
-- Wellnara — Flyway V16: remove the personal calendar subscription feed
--
-- The one-way webcal/ICS subscription feed (introduced in V6) is retired:
-- the application only ever exported appointments to the feed, changes made
-- in the external calendar never returned, so the feature is dropped in
-- favour of the per-appointment "add to calendar" action, which stays.
--
-- Dropping the table also removes its unique constraints and the foreign key
-- to users (fk_calendar_subscriptions_user). No other table references it.
-- =====================================================================

drop table if exists calendar_subscriptions;
