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
 * A single append-only entry in a wallet's ledger.
 *
 * <p>Entries are inserted, never updated or deleted; a correction is a new
 * compensating entry. All columns are therefore non-updatable. Depending on its
 * {@link WalletEntryType#kind() kind}, an entry carries either a monetary
 * {@code amount} (money ledger) or a {@code sessionCount} together with the
 * originating {@link ServicePackage} (session ledger). Use the {@link #money}
 * and {@link #session} factory methods, which set the correct fields for each
 * kind.
 */
@Entity
@Table(name = "wallet_entries")
public class WalletEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "wallet_id", nullable = false, updatable = false)
    private Wallet wallet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private WalletEntryType type;

    /** Monetary amount for money-ledger entries; {@code null} for session entries. */
    @Column(updatable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Session count for session-ledger entries; {@code null} for money entries. */
    @Column(updatable = false)
    private Integer sessionCount;

    /** Currency of the entry; always equal to the wallet currency. */
    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    /** Package this entry belongs to, for {@code PACKAGE_*} entries; otherwise {@code null}. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_package_id", updatable = false)
    private ServicePackage servicePackage;

    /** Appointment this entry settles or holds against; {@code null} for top-ups and grants. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", updatable = false)
    private Appointment appointment;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** User who caused the entry (provider for top-ups/grants, client for holds). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private User createdBy;

    @Column(length = 1000, updatable = false)
    private String comment;

    /**
     * Required by JPA.
     */
    protected WalletEntry() {
    }

    private WalletEntry(Wallet wallet,
                        WalletEntryType type,
                        BigDecimal amount,
                        Integer sessionCount,
                        ServicePackage servicePackage,
                        Appointment appointment,
                        User createdBy,
                        LocalDateTime createdAt,
                        String comment) {
        this.wallet = wallet;
        this.type = type;
        this.amount = amount;
        this.sessionCount = sessionCount;
        this.servicePackage = servicePackage;
        this.appointment = appointment;
        this.currency = wallet.getCurrency();
        this.createdBy = createdBy;
        this.createdAt = createdAt;
        this.comment = comment;
    }

    /**
     * Creates a money-ledger entry.
     *
     * @param wallet target wallet
     * @param type a money type ({@link WalletEntryType#isMoney()} must be true)
     * @param amount monetary amount in the wallet currency (may be negative only for {@code ADJUSTMENT})
     * @param appointment appointment the entry relates to, or {@code null}
     * @param createdBy user who caused the entry
     * @param createdAt creation timestamp in UTC
     * @param comment optional free-text note
     * @return a new, unsaved money entry
     * @throws IllegalArgumentException if {@code type} is not a money type
     */
    public static WalletEntry money(Wallet wallet,
                                    WalletEntryType type,
                                    BigDecimal amount,
                                    Appointment appointment,
                                    User createdBy,
                                    LocalDateTime createdAt,
                                    String comment) {
        if (!type.isMoney()) {
            throw new IllegalArgumentException("Not a money entry type: " + type);
        }
        return new WalletEntry(wallet, type, amount, null, null, appointment, createdBy, createdAt, comment);
    }

    /**
     * Creates a session-ledger entry for a service package.
     *
     * @param wallet target wallet
     * @param type a session type ({@link WalletEntryType#isSession()} must be true)
     * @param sessionCount number of sessions moved (positive)
     * @param servicePackage package the sessions belong to
     * @param appointment appointment the entry relates to, or {@code null}
     * @param createdBy user who caused the entry
     * @param createdAt creation timestamp in UTC
     * @param comment optional free-text note
     * @return a new, unsaved session entry
     * @throws IllegalArgumentException if {@code type} is not a session type
     */
    public static WalletEntry session(Wallet wallet,
                                      WalletEntryType type,
                                      Integer sessionCount,
                                      ServicePackage servicePackage,
                                      Appointment appointment,
                                      User createdBy,
                                      LocalDateTime createdAt,
                                      String comment) {
        if (!type.isSession()) {
            throw new IllegalArgumentException("Not a session entry type: " + type);
        }
        return new WalletEntry(wallet, type, null, sessionCount, servicePackage, appointment, createdBy, createdAt, comment);
    }

    public Long getId() {
        return id;
    }

    public Wallet getWallet() {
        return wallet;
    }

    public WalletEntryType getType() {
        return type;
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

    public ServicePackage getServicePackage() {
        return servicePackage;
    }

    public Appointment getAppointment() {
        return appointment;
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
