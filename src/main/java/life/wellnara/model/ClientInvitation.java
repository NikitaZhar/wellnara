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
import java.util.UUID;

/**
 * Invitation for client registration from a provider.
 * Removed after successful registration.
 */
@Entity
@Table(name = "client_invitations")
public class ClientInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true, updatable = false)
    private String token;

    @Column(nullable = false, updatable = false)
    private LocalDateTime invitedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    /**
     * Required by JPA.
     */
    protected ClientInvitation() {
    }

    /**
     * Creates a client invitation.
     *
     * @param provider  provider who invites the client
     * @param email     invited client email
     * @param invitedAt moment the invitation was issued (UTC)
     * @param expiresAt moment after which the invitation is no longer valid (UTC)
     */
    public ClientInvitation(User provider, String email, LocalDateTime invitedAt, LocalDateTime expiresAt) {
        this.provider = provider;
        this.email = email;
        this.token = UUID.randomUUID().toString();
        this.invitedAt = invitedAt;
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public User getProvider() {
        return provider;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }

    public LocalDateTime getInvitedAt() {
        return invitedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    /**
     * Tells whether this invitation has expired at the given moment.
     *
     * @param now current moment (UTC)
     * @return true if the invitation is no longer valid
     */
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }
}
