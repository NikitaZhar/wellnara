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
 * Invitation for client registration from provider.
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

    /**
     * Required by JPA.
     */
    protected ClientInvitation() {
    }

    /**
     * Creates client invitation.
     *
     * @param provider provider who invites client
     * @param email invited client email
     */
    public ClientInvitation(User provider, String email) {
        this.provider = provider;
        this.email = email;
        this.token = UUID.randomUUID().toString();
        this.invitedAt = LocalDateTime.now();
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
}