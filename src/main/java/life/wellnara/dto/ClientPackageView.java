package life.wellnara.dto;

/**
 * A client's active package for one offering, aggregated across every package the
 * client holds for that service.
 *
 * <p>{@code remaining} sessions can still be booked, {@code pending} are already
 * booked and awaiting delivery, {@code total} is what was originally granted.
 */
public class ClientPackageView {

    private final Long offeringId;
    private final String offeringName;
    private final boolean offeringActive;
    private final int total;
    private final int remaining;
    private final int pending;

    /**
     * Creates a client package view.
     *
     * @param offeringId     offering the sessions apply to (booking target)
     * @param offeringName   offering display name
     * @param offeringActive whether the offering can still be booked
     * @param total          sessions originally granted
     * @param remaining      sessions available to book now
     * @param pending        sessions already booked and awaiting delivery
     */
    public ClientPackageView(Long offeringId,
                             String offeringName,
                             boolean offeringActive,
                             int total,
                             int remaining,
                             int pending) {
        this.offeringId = offeringId;
        this.offeringName = offeringName;
        this.offeringActive = offeringActive;
        this.total = total;
        this.remaining = remaining;
        this.pending = pending;
    }

    public Long getOfferingId() {
        return offeringId;
    }

    public String getOfferingName() {
        return offeringName;
    }

    public boolean isOfferingActive() {
        return offeringActive;
    }

    public int getTotal() {
        return total;
    }

    public int getRemaining() {
        return remaining;
    }

    public int getPending() {
        return pending;
    }

    /** Whether the client can book another session from this package now. */
    public boolean canBookNext() {
        return remaining >= 1 && offeringActive;
    }
}
