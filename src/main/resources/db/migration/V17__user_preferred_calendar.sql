-- =====================================================================
-- Wellnara — Flyway V17: user's preferred external calendar
--
-- Stores which calendar (GOOGLE / OUTLOOK / APPLE) a user picked for the
-- per-appointment "add to calendar" button. Nullable: null means no choice
-- yet, and the appointments page prompts the user to pick one in their profile.
--
-- Enum is persisted as its name (EnumType.STRING); varchar(16) leaves room
-- for the values without truncation.
-- =====================================================================

alter table users
    add column preferred_calendar varchar(16);
