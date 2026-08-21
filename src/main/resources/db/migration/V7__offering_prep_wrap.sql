-- =====================================================================
-- Wellnara — Flyway V7: per-offering preparation / wrap-up buffers.
--
-- Providers may reserve time before a session (preparation) and after it
-- (writing up results). These buffers are provider-only: the client never sees
-- them and books the session time alone. They pad the provider's busy footprint
-- (so sessions cannot be booked back-to-back without the gap) and the exported
-- provider calendar block.
--
-- Existing offerings keep the previous behaviour (no buffers) by defaulting to
-- 0. New offerings default their wrap-up to 15 at the application layer (the
-- create form), not here, so this migration does not change any existing
-- schedule.
-- =====================================================================

alter table offerings
    add column prep_minutes integer not null default 0;

alter table offerings
    add column wrap_minutes integer not null default 0;
