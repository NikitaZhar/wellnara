-- =====================================================================
-- Wellnara — Flyway V11: drop the per-offering minimum package size.
--
-- A client may now buy any number of sessions from 1 up to the maximum, so a
-- provider-set lower bound no longer applies (a package of one is just a single
-- session at the standard price). The column is removed to avoid dead config;
-- the maximum (max_package_sessions) is kept.
-- =====================================================================

alter table offerings
    drop column min_package_sessions;
