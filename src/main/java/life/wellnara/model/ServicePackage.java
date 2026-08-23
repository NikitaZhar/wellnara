package life.wellnara.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * A package of pre-paid sessions on a client's wallet: which offering the sessions
 * are for, how many, and at what price. Immutable except for its {@link #status}
 * lifecycle ({@code REQUESTED → ACTIVE|REJECTED}).
 *
 * <p>A provider-granted package (offline-paid) is {@code ACTIVE} from the start; a
 * client-requested one starts {@code REQUESTED} with its price held and becomes
 * {@code ACTIVE} on approval. The <em>remaining</em> session count is not stored —
 * it is a fold over the package's {@code PACKAGE_*} {@link WalletEntry} rows.
 */
@Entity
@Table(name = "service_packages")
public class ServicePackage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false, updatable = false)
    private Offering offering;

    /** Number of sessions granted by this package. */
    @Column(nullable = false, updatable = false)
    private Integer totalSessions;

    /** Total price paid for the package, in the wallet currency. */
    @Column(nullable = false, updatable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    /**
     * Start (UTC) the client picked for the first session of this package when
     * requesting it; {@code null} for a provider-granted package, whose sessions
     * are all booked later. Read once, on approval, to schedule the first
     * appointment.
     */
    @Column(name = "first_session_start_utc", updatable = false)
    private LocalDateTime firstSessionStartUtc;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(length = 1000, updatable = false)
    private String comment;

    /** Lifecycle status; only {@link PackageStatus#ACTIVE} packages cover bookings. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PackageStatus status;

    /**
     * Required by JPA.
     */
    protected ServicePackage() {
    }

    /**
     * Creates a service package.
     *
     * @param wallet wallet the package belongs to
     * @param offering offering the sessions apply to
     * @param totalSessions number of sessions (must be positive)
     * @param price total price, in the wallet currency
     * @param currency ISO 4217 currency code (equals the wallet currency)
     * @param createdBy user who created the package (provider grant, or client request)
     * @param createdAt creation timestamp in UTC (supplied by the time service)
     * @param comment optional free-text note
     * @param status initial lifecycle status
     * @param firstSessionStartUtc start (UTC) the client picked for the first session, or {@code null}
     */
    public ServicePackage(Wallet wallet,
                          Offering offering,
                          Integer totalSessions,
                          BigDecimal price,
                          String currency,
                          User createdBy,
                          LocalDateTime createdAt,
                          String comment,
                          PackageStatus status,
                          LocalDateTime firstSessionStartUtc) {
        this.wallet = wallet;
        this.offering = offering;
        this.totalSessions = totalSessions;
        this.price = price;
        this.currency = currency;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.comment = comment;
        this.status = status;
        this.firstSessionStartUtc = firstSessionStartUtc;
    }

    /**
     * Approves a requested package: {@code REQUESTED → ACTIVE}.
     *
     * @throws IllegalStateException if the package is not awaiting approval
     */
    public void activate() {
        requireRequested();
        this.status = PackageStatus.ACTIVE;
    }

    /**
     * Declines a requested package: {@code REQUESTED → REJECTED}.
     *
     * @throws IllegalStateException if the package is not awaiting approval
     */
    public void reject() {
        requireRequested();
        this.status = PackageStatus.REJECTED;
    }

    private void requireRequested() {
        if (status != PackageStatus.REQUESTED) {
            throw new IllegalStateException("Package is not awaiting approval");
        }
    }

    public Long getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public Offering getOffering() {
        return offering;
    }

    public Integer getTotalSessions() {
        return totalSessions;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public String getCurrency() {
        return currency;
    }

    public LocalDateTime getFirstSessionStartUtc() {
        return firstSessionStartUtc;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getComment() {
        return comment;
    }

    public PackageStatus getStatus() {
        return status;
    }
}
