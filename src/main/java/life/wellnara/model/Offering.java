package life.wellnara.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Offering created and owned by provider.
 */
@Entity
@Table(name = "offerings")
public class Offering {

    /** Upper bound on sessions per package when the provider sets no explicit maximum. */
    public static final int PACKAGE_SESSIONS_CAP = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSession;

    @Column(nullable = false)
    private Integer durationMinutes;

    /**
     * Provider-only buffer reserved before the session starts (preparation).
     * Never shown to the client; it only pads the provider's busy footprint and
     * the exported calendar block. Defaults to 0 (no buffer).
     */
    @Column(name = "prep_minutes", nullable = false)
    private int prepMinutes;

    /**
     * Provider-only buffer reserved after the session ends (writing up results).
     * Never shown to the client; it only pads the provider's busy footprint and
     * the exported calendar block. New offerings default to 15 via the create
     * form; the raw constructor leaves it at 0.
     */
    @Column(name = "wrap_minutes", nullable = false)
    private int wrapMinutes;

    /**
     * Per-session price when this service is sold as a package (abonnement).
     * {@code null} means the service is not sold as a package. When set, it must
     * not exceed {@link #pricePerSession} (a package is never dearer per session).
     */
    @Column(name = "package_price_per_session", precision = 10, scale = 2)
    private BigDecimal packagePricePerSession;

    /** Minimum sessions a package of this service may contain; {@code null} → 1. */
    @Column(name = "min_package_sessions")
    private Integer minPackageSessions;

    /** Maximum sessions a package of this service may contain; {@code null} → {@value #PACKAGE_SESSIONS_CAP}. */
    @Column(name = "max_package_sessions")
    private Integer maxPackageSessions;

    @Column(nullable = false)
    private boolean active;

    /**
     * ISO 4217 currency code the price is quoted in, inherited from the provider.
     *
     * <p>Nullable in step 3.1 (column introduced); step 3.3 makes offering
     * currency follow the provider's wallet currency and enforces its presence.
     */
    @Column(length = 3)
    private String currency;

    /**
     * Required by JPA.
     */
    public Offering() {
    }

    /**
     * Creates offering.
     *
     * @param provider provider owner
     * @param name offering name
     * @param description offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     */
    public Offering(User provider,
                    String name,
                    String description,
                    BigDecimal pricePerSession,
                    Integer durationMinutes) {
        this(provider, name, description, pricePerSession, durationMinutes, 0, 0);
    }

    /**
     * Creates offering with explicit prep/wrap buffers.
     *
     * @param provider        provider owner
     * @param name            offering name
     * @param description     offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     * @param prepMinutes     provider-only buffer before the session (minutes)
     * @param wrapMinutes     provider-only buffer after the session (minutes)
     */
    public Offering(User provider,
                    String name,
                    String description,
                    BigDecimal pricePerSession,
                    Integer durationMinutes,
                    int prepMinutes,
                    int wrapMinutes) {
        this.provider = provider;
        this.name = name;
        this.description = description;
        this.pricePerSession = pricePerSession;
        this.durationMinutes = durationMinutes;
        this.prepMinutes = prepMinutes;
        this.wrapMinutes = wrapMinutes;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public User getProvider() {
        return provider;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPricePerSession() {
        return pricePerSession;
    }

    public void setPricePerSession(BigDecimal pricePerSession) {
        this.pricePerSession = pricePerSession;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public int getPrepMinutes() {
        return prepMinutes;
    }

    public void setPrepMinutes(int prepMinutes) {
        this.prepMinutes = prepMinutes;
    }

    public int getWrapMinutes() {
        return wrapMinutes;
    }

    public void setWrapMinutes(int wrapMinutes) {
        this.wrapMinutes = wrapMinutes;
    }

    public BigDecimal getPackagePricePerSession() {
        return packagePricePerSession;
    }

    public void setPackagePricePerSession(BigDecimal packagePricePerSession) {
        this.packagePricePerSession = packagePricePerSession;
    }

    public Integer getMinPackageSessions() {
        return minPackageSessions;
    }

    public void setMinPackageSessions(Integer minPackageSessions) {
        this.minPackageSessions = minPackageSessions;
    }

    public Integer getMaxPackageSessions() {
        return maxPackageSessions;
    }

    public void setMaxPackageSessions(Integer maxPackageSessions) {
        this.maxPackageSessions = maxPackageSessions;
    }

    /**
     * Whether this service can be sold as a package.
     *
     * @return {@code true} once a package per-session price is set
     */
    public boolean isPackageable() {
        return packagePricePerSession != null;
    }

    /**
     * Smallest allowed session count for a package of this service.
     *
     * @return the configured minimum, or 1 when unset
     */
    public int effectiveMinPackageSessions() {
        return minPackageSessions != null ? minPackageSessions : 1;
    }

    /**
     * Largest allowed session count for a package of this service.
     *
     * @return the configured maximum, or {@link #PACKAGE_SESSIONS_CAP} when unset
     */
    public int effectiveMaxPackageSessions() {
        return maxPackageSessions != null ? maxPackageSessions : PACKAGE_SESSIONS_CAP;
    }

    /**
     * Default total price of a package of {@code sessions} sessions of this service.
     *
     * @param sessions number of sessions in the package
     * @return the package per-session price times the session count
     * @throws IllegalStateException if this service is not sold as a package
     */
    public BigDecimal packagePriceFor(int sessions) {
        if (!isPackageable()) {
            throw new IllegalStateException("Offering is not sold as a package");
        }
        return packagePricePerSession.multiply(BigDecimal.valueOf(sessions));
    }

    public boolean isActive() {
        return active;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }
}
