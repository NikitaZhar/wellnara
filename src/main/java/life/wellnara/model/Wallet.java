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
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

/**
 * Wallet of a single client held with a single provider.
 *
 * <p>A client is linked to exactly one provider, so a client has exactly one
 * wallet (enforced by a unique constraint on {@code client_id}). The wallet
 * stores no balance: available and held figures are a fold over
 * {@link WalletEntry} rows (see the money model in the plan). Currency is fixed
 * at creation and, once the ledger is non-empty, must not change.
 *
 * <p>The optimistic-lock {@link Version} lets concurrent writers detect lost
 * updates; the database has the final word (step 3.7 adds the supporting unique
 * constraints).
 */
@Entity
@Table(
        name = "wallets",
        uniqueConstraints = @UniqueConstraint(name = "uk_wallets_client", columnNames = "client_id")
)
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false, updatable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false, updatable = false)
    private User provider;

    /** ISO 4217 currency code (e.g. {@code "USD"}); one wallet, one currency. */
    @Column(nullable = false, length = 3)
    private String currency;

    @Version
    @Column(nullable = false)
    private Long version;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * Required by JPA.
     */
    protected Wallet() {
    }

    /**
     * Creates a wallet for a client-provider pair.
     *
     * @param client the client who owns the wallet
     * @param provider the provider the wallet is held with
     * @param currency ISO 4217 currency code of the provider
     * @param createdAt creation timestamp in UTC (supplied by the time service)
     */
    public Wallet(User client, User provider, String currency, LocalDateTime createdAt) {
        this.client = client;
        this.provider = provider;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getClient() {
        return client;
    }

    public User getProvider() {
        return provider;
    }

    public String getCurrency() {
        return currency;
    }

    public Long getVersion() {
        return version;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
