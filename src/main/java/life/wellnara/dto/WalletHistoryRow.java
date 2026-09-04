package life.wellnara.dto;

import life.wellnara.model.WalletEntryType;
import life.wellnara.model.WalletLedgerKind;

import java.math.BigDecimal;

/**
 * One movement in a wallet's ledger, prepared for display.
 *
 * <p>A history row is a read projection of a single {@code WalletEntry}. The
 * {@code timestamp} is already formatted in the provider's calendar timezone (the
 * same zone appointments are shown in) — the ledger stores UTC, the view shows
 * local time. {@code typeLabel} is a human-readable name for the movement, so
 * templates never expose the raw enum. A row carries either a monetary
 * {@code amount} (money ledger) or a {@code sessionCount} with the
 * {@code offeringName} (session ledger), never both; templates pick which to show
 * from {@link #isMoney()} / {@link #isSession()}.
 */
public class WalletHistoryRow {

    private final String timestamp;
    private final WalletEntryType type;
    private final String typeLabel;
    private final BigDecimal amount;
    private final Integer sessionCount;
    private final String currency;
    private final String offeringName;
    private final String comment;
    private final boolean service;

    /**
     * Creates a wallet history row.
     *
     * @param timestamp    entry time, pre-formatted in the provider timezone
     * @param type         ledger entry type
     * @param typeLabel    human-readable label for the entry type
     * @param amount       monetary amount for money entries, or {@code null}
     * @param sessionCount session count for session entries, or {@code null}
     * @param currency     ISO 4217 currency code of the wallet
     * @param offeringName offering the sessions apply to for package entries, or {@code null}
     * @param comment      optional free-text note captured when the entry was written
     * @param service      whether this row is a rendered service (a delivered/no-show/late-cancel
     *                     appointment, paid with money or a package session), as opposed to a pure
     *                     money movement (top-up, package purchase, adjustment); drives the
     *                     "Services" journal filter
     */
    public WalletHistoryRow(String timestamp,
                            WalletEntryType type,
                            String typeLabel,
                            BigDecimal amount,
                            Integer sessionCount,
                            String currency,
                            String offeringName,
                            String comment,
                            boolean service) {
        this.timestamp = timestamp;
        this.type = type;
        this.typeLabel = typeLabel;
        this.amount = amount;
        this.sessionCount = sessionCount;
        this.currency = currency;
        this.offeringName = offeringName;
        this.comment = comment;
        this.service = service;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public WalletEntryType getType() {
        return type;
    }

    public String getTypeLabel() {
        return typeLabel;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public Integer getSessionCount() {
        return sessionCount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getOfferingName() {
        return offeringName;
    }

    public String getComment() {
        return comment;
    }

    /**
     * Whether this row belongs to the money ledger.
     *
     * @return {@code true} for money movements
     */
    public boolean isMoney() {
        return type.kind() == WalletLedgerKind.MONEY;
    }

    /**
     * Whether this row belongs to the session ledger.
     *
     * @return {@code true} for package-session movements
     */
    public boolean isSession() {
        return type.kind() == WalletLedgerKind.SESSION;
    }

    /**
     * Whether this row represents a rendered service (an appointment that was
     * delivered, a no-show, or a late cancellation — settled with money or a
     * package session). Used by the client journal's "Services" filter; a pure
     * money movement such as a top-up, a package purchase or an adjustment is not
     * a service.
     *
     * @return {@code true} for a service movement
     */
    public boolean isService() {
        return service;
    }
}
