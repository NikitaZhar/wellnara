package life.wellnara.dto;

import java.math.BigDecimal;

/**
 * Optional package (abonnement) pricing for an offering, as entered on the
 * offering form. All fields are nullable: a {@code null} per-session price means
 * the service is not sold as a package; {@code null} min/max fall back to the
 * offering's defaults.
 *
 * @param pricePerSession discounted per-session price inside a package, or {@code null}
 * @param minSessions     minimum sessions a package may contain, or {@code null}
 * @param maxSessions     maximum sessions a package may contain, or {@code null}
 */
public record PackagePricing(BigDecimal pricePerSession, Integer minSessions, Integer maxSessions) {

    /** Empty pricing — the service is not sold as a package. */
    public static PackagePricing none() {
        return new PackagePricing(null, null, null);
    }

    /**
     * Whether a package per-session price was provided.
     *
     * @return {@code true} when the service is to be sold as a package
     */
    public boolean isPackageable() {
        return pricePerSession != null;
    }
}
