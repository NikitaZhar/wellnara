package life.wellnara.dto;

/**
 * Remaining sessions of one service package, prepared for display.
 *
 * <p>A read projection folded from the package's session ledger:
 * {@code available} sessions can still be booked, {@code held} are reserved
 * against pending appointments, {@code total} is what the client still owns
 * (available + held). Fully consumed packages are omitted by the query service.
 */
public class PackageRemainder {

    private final Long packageId;
    private final String offeringName;
    private final int available;
    private final int held;

    /**
     * Creates a package remainder row.
     *
     * @param packageId    identifier of the package (for provider actions such as refund)
     * @param offeringName offering the package sessions apply to
     * @param available    sessions still bookable
     * @param held         sessions reserved against pending appointments
     */
    public PackageRemainder(Long packageId, String offeringName, int available, int held) {
        this.packageId = packageId;
        this.offeringName = offeringName;
        this.available = available;
        this.held = held;
    }

    public Long getPackageId() {
        return packageId;
    }

    public String getOfferingName() {
        return offeringName;
    }

    public int getAvailable() {
        return available;
    }

    public int getHeld() {
        return held;
    }

    /**
     * Returns the sessions still owned by the client for this package.
     *
     * @return {@code available + held}
     */
    public int getTotal() {
        return available + held;
    }
}
