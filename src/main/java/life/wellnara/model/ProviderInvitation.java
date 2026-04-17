package life.wellnara.model;

import jakarta.persistence.*;

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

    protected ProviderInvitation() {
    }

    public ProviderInvitation(String email) {
        this.email = email;
        this.token = UUID.randomUUID().toString();
    }

    public String getEmail() {
        return email;
    }

    public String getToken() {
        return token;
    }
}