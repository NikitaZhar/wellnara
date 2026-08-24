-- The wallet ledger gained a PACKAGE_REVOKE entry type: a provider refund voids
-- the package's unused sessions (WalletCommandService.refundPackage). V2 created
-- the wallet_entries.type CHECK with only the first nine types, so inserting a
-- PACKAGE_REVOKE row fails the constraint at runtime. Widen the CHECK to include it.
alter table wallet_entries
    drop constraint wallet_entries_type_check;

alter table wallet_entries
    add constraint wallet_entries_type_check
    check (type in (
        'TOP_UP', 'HOLD', 'RELEASE', 'SETTLE', 'ADJUSTMENT',
        'PACKAGE_GRANT', 'PACKAGE_HOLD', 'PACKAGE_RELEASE', 'PACKAGE_CONSUME', 'PACKAGE_REVOKE'
    ));
