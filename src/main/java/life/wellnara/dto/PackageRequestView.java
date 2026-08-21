package life.wellnara.dto;

import java.math.BigDecimal;

/**
 * A pending package request, shown to the provider (to approve or decline) and to
 * the client (to see it is awaiting approval).
 */
public class PackageRequestView {

    private final Long packageId;
    private final String clientName;
    private final String offeringName;
    private final int sessions;
    private final BigDecimal price;
    private final String currency;

    /**
     * Creates a package request view.
     *
     * @param packageId    package identifier (approve / decline target)
     * @param clientName   client who requested it (for the provider view; may be {@code null})
     * @param offeringName offering the package is for
     * @param sessions     number of sessions requested
     * @param price        total price held, in the wallet currency
     * @param currency     ISO 4217 currency code
     */
    public PackageRequestView(Long packageId,
                              String clientName,
                              String offeringName,
                              int sessions,
                              BigDecimal price,
                              String currency) {
        this.packageId = packageId;
        this.clientName = clientName;
        this.offeringName = offeringName;
        this.sessions = sessions;
        this.price = price;
        this.currency = currency;
    }

    public Long getPackageId() {
        return packageId;
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
}
