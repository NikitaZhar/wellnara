package life.wellnara.model;

/**
 * Type of an append-only wallet ledger entry.
 *
 * <p>Two parallel ledgers share this enum:
 * a money ledger ({@link #TOP_UP}, {@link #HOLD}, {@link #RELEASE},
 * {@link #SETTLE}, {@link #ADJUSTMENT}) whose entries carry a monetary
 * {@code amount}, and a session ledger for service packages
 * ({@link #PACKAGE_GRANT}, {@link #PACKAGE_HOLD}, {@link #PACKAGE_RELEASE},
 * {@link #PACKAGE_CONSUME}) whose entries carry a {@code sessionCount}.
 *
 * <p>Balance figures (available / held money, available / held sessions) are
 * never stored; they are a fold over the entries of the given kind.
 */
public enum WalletEntryType {

    /** Provider manually records money received outside the system: {@code available += amount}. */
    TOP_UP(WalletLedgerKind.MONEY),

    /** Client requested a service: {@code available -= amount}, {@code held += amount}. */
    HOLD(WalletLedgerKind.MONEY),

    /** Hold is returned to the client: {@code available += amount}, {@code held -= amount}. */
    RELEASE(WalletLedgerKind.MONEY),

    /** Final charge for a delivered service: {@code held -= amount}. */
    SETTLE(WalletLedgerKind.MONEY),

    /** Manual correction by the provider: {@code available += amount} (amount may be negative). */
    ADJUSTMENT(WalletLedgerKind.MONEY),

    /** Provider granted a package: {@code availableSessions += sessionCount}. */
    PACKAGE_GRANT(WalletLedgerKind.SESSION),

    /** A package session is reserved: {@code availableSessions -= sessionCount}, {@code heldSessions += sessionCount}. */
    PACKAGE_HOLD(WalletLedgerKind.SESSION),

    /** A package hold is returned: {@code availableSessions += sessionCount}, {@code heldSessions -= sessionCount}. */
    PACKAGE_RELEASE(WalletLedgerKind.SESSION),

    /** A held package session is finally consumed: {@code heldSessions -= sessionCount}. */
    PACKAGE_CONSUME(WalletLedgerKind.SESSION);

    private final WalletLedgerKind kind;

    WalletEntryType(WalletLedgerKind kind) {
        this.kind = kind;
    }

    /**
     * Returns which ledger this entry type belongs to.
     *
     * @return money or session ledger kind
     */
    public WalletLedgerKind kind() {
        return kind;
    }

    /**
     * Whether this type carries a monetary amount.
     *
     * @return {@code true} for money-ledger types
     */
    public boolean isMoney() {
        return kind == WalletLedgerKind.MONEY;
    }

    /**
     * Whether this type carries a session count.
     *
     * @return {@code true} for session-ledger types
     */
    public boolean isSession() {
        return kind == WalletLedgerKind.SESSION;
    }
}
