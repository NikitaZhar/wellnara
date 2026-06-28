package life.wellnara.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Invitation for provider registration.
 * Removed after successful registration.
 */
@Entity
@Table(name = "provider_invitations")
public class ProviderInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true, updatable = false)
    private String token;

    @Column(nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    /**
     * Required by JPA.
     */
    protected ProviderInvitation() {
    }

    /**
     * Creates a provider invitation.
     *
     * @param email     invited provider email
     * @param expiresAt moment after which the invitation is no longer valid (UTC)
     */
    public ProviderInvitation(String email, LocalDateTime expiresAt) {
        this.email = email;
        this.token = UUID.randomUUID().toString();
        this.expiresAt = expiresAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
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
