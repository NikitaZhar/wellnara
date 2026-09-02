package life.wellnara.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One reservation currently holding money on the client's wallet, prepared for
 * the "held — against what" breakdown.
 *
 * <p>A read projection over the money ledger: the {@code amount} is the money
 * still held for this subject (a single appointment, or a whole package awaiting
 * approval). {@code startDateTime} is the session start in the provider timezone,
 * or {@code null} when the subject has no single time. {@code sessions} is the
 * package size when the hold is for a package, or {@code null} for a single
 * appointment.
 */
public class HeldItemView {

    private final String offeringName;
    private final LocalDateTime startDateTime;
    private final Integer sessions;
    private final BigDecimal amount;
    private final String currency;

    /**
     * Creates a held-item row.
     *
     * @param offeringName  offering the reservation is for
     * @param startDateTime session start in the provider timezone, or {@code null}
     * @param sessions      package size, or {@code null} for a single appointment
     * @param amount        money still held for this reservation
     * @param currency      ISO 4217 currency code of the wallet
     */
    public HeldItemView(String offeringName,
                        LocalDateTime startDateTime,
                        Integer sessions,
                        BigDecimal amount,
                        String currency) {
        this.offeringName = offeringName;
        this.startDateTime = startDateTime;
        this.sessions = sessions;
        this.amount = amount;
        this.currency = currency;
    }

    public String getOfferingName() {
        return offeringName;
    }

    public LocalDateTime getStartDateTime() {
        return startDateTime;
    }

    public Integer getSessions() {
        return sessions;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }
}
