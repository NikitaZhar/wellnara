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
import java.time.LocalDateTime;

/**
 * A package of pre-paid sessions granted by a provider to a client's wallet.
 *
 * <p>This is the immutable grant record: which offering the sessions are for,
 * how many were granted, and at what price. The <em>remaining</em> session count
 * is not stored here — it is a fold over the package's {@code PACKAGE_*}
 * {@link WalletEntry} rows (grant minus holds and consumptions).
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

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(length = 1000, updatable = false)
    private String comment;

    /**
     * Required by JPA.
     */
    protected ServicePackage() {
    }

    /**
     * Creates a service package grant.
     *
     * @param wallet wallet the package is granted to
     * @param offering offering the sessions apply to
     * @param totalSessions number of sessions granted (must be positive)
     * @param price total price paid, in the wallet currency
     * @param currency ISO 4217 currency code (equals the wallet currency)
     * @param createdBy provider who granted the package
     * @param createdAt creation timestamp in UTC (supplied by the time service)
     * @param comment optional free-text note
     */
    public ServicePackage(Wallet wallet,
                          Offering offering,
                          Integer totalSessions,
                          BigDecimal price,
                          String currency,
                          User createdBy,
                          LocalDateTime createdAt,
                          String comment) {
        this.wallet = wallet;
        this.offering = offering;
        this.totalSessions = totalSessions;
        this.price = price;
        this.currency = currency;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.comment = comment;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public User getCreatedBy() {
        return createdBy;
    }

    public String getComment() {
        return comment;
    }
}
