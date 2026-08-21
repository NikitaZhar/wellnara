-- =====================================================================
-- Wellnara — Flyway V8: per-offering package (abonnement) pricing.
--
-- A service may optionally be sold as a package of pre-paid sessions. The
-- provider sets a discounted per-session price and, optionally, the minimum and
-- maximum session count a package may contain. A null package price means the
-- service is not sold as a package. Existing services keep the previous
-- behaviour (not packageable) by defaulting to null.
-- =====================================================================

alter table offerings
    add column package_price_per_session numeric(10, 2);

alter table offerings
    add column min_package_sessions integer;

alter table offerings
    add column max_package_sessions integer;
