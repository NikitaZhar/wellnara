package life.wellnara.model;

/**
 * Which of the two parallel wallet ledgers an entry belongs to.
 */
public enum WalletLedgerKind {

    /** Monetary ledger; entries carry a {@code BigDecimal amount}. */
    MONEY,

    /** Session ledger for service packages; entries carry an {@code Integer sessionCount}. */
    SESSION
}
