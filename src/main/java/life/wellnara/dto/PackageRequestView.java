package life.wellnara.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A pending package request, shown to the provider (to approve or decline) and to
 * the client (to see it is awaiting approval).
 */
public class PackageRequestView {

    private final Long packageId;
    private final Long offeringId;
    private final String clientName;
    private final String offeringName;
    private final int sessions;
    private final BigDecimal price;
    private final String currency;
    private final LocalDateTime firstSessionStart;

    /**
     * Creates a package request view.
     *
     * @param packageId         package identifier (approve / decline target)
     * @param offeringId        offering the package is for (booking target)
     * @param clientName        client who requested it (for the provider view; may be {@code null})
     * @param offeringName      offering the package is for
     * @param sessions          number of sessions requested
     * @param price             total price held, in the wallet currency
     * @param currency          ISO 4217 currency code
     * @param firstSessionStart start of the requested first session in the provider's
     *                          timezone, or {@code null} if none was chosen
     */
    public PackageRequestView(Long packageId,
                              Long offeringId,
                              String clientName,
                              String offeringName,
                              int sessions,
                              BigDecimal price,
                              String currency,
                              LocalDateTime firstSessionStart) {
        this.packageId = packageId;
        this.offeringId = offeringId;
        this.clientName = clientName;
        this.offeringName = offeringName;
        this.sessions = sessions;
        this.price = price;
        this.currency = currency;
        this.firstSessionStart = firstSessionStart;
    }

    public Long getPackageId() {
        return packageId;
    }

    public Long getOfferingId() {
        return offeringId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getOfferingName() {
        return offeringName;
    }

    public int getSessions() {
        return sessions;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getFirstSessionStart() {
        return firstSessionStart;
    }
}
