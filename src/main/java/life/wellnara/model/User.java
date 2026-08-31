package life.wellnara.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Represents a system user.
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /**
     * ISO 4217 currency code of the provider's wallet (e.g. {@code "USD"}).
     *
     * <p>Set only for {@link UserRole#PROVIDER} users; {@code null} for admins and
     * clients. Nullable in step 3.1 (column introduced); step 3.3 wires the
     * currency semantics and enforces its presence for providers.
     */
    @Column(length = 3)
    private String currency;

    /**
     * Preferred UI and notification language as an ISO 639 code (e.g. {@code ru}
     * or {@code en}), captured from the active locale at registration.
     *
     * <p>{@code null} when the user has no stored preference; the application then
     * falls back to the default language.
     */
    @Column(length = 8)
    private String language;

    /**
     * Required by JPA.
     */
    public User() {
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public UserRole getRole() {
        return role;
    }

    public void setRole(UserRole role) {
        this.role = role;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }
}
