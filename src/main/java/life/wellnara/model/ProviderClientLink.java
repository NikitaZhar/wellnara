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

import java.time.LocalDateTime;

/**
 * Permanent link between provider and client.
 */
@Entity
@Table(name = "provider_client_links")
public class ProviderClientLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @Column(nullable = false, updatable = false)
    private LocalDateTime invitedAt;

    /**
     * Required by JPA.
     */
    protected ProviderClientLink() {
    }

    /**
     * Creates provider-client link.
     *
     * @param provider provider user
     * @param client client user
     * @param invitedAt client invitation date
     */
    public ProviderClientLink(User provider, User client, LocalDateTime invitedAt) {
        this.provider = provider;
        this.client = client;
        this.invitedAt = invitedAt;
    }

    public Long getId() {
        return id;
    }

    public User getProvider() {
        return provider;
    }

    public User getClient() {
        return client;
    }

    public LocalDateTime getInvitedAt() {
        return invitedAt;
    }
}