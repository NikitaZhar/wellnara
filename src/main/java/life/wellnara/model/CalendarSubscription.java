package life.wellnara.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * A user's personal calendar feed subscription.
 *
 * <p>Holds one opaque, revocable token per user; the token backs a read-only
 * {@code webcal}/ICS feed of that user's appointments. Disabling keeps the row
 * for audit but stops the feed from resolving; regenerating issues a fresh token
 * and re-enables the feed, invalidating the previous URL.
 */
@Entity
@Table(name = "calendar_subscriptions")
public class CalendarSubscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private boolean enabled;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime revokedAt;

    /**
     * Required by JPA.
     */
    protected CalendarSubscription() {
    }

    /**
     * Creates an enabled subscription with the given token.
     *
     * @param user      subscription owner
     * @param token     opaque feed token
     * @param createdAt creation time in UTC
     */
    public CalendarSubscription(User user, String token, LocalDateTime createdAt) {
        this.user = user;
        this.token = token;
        this.enabled = true;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getToken() {
        return token;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    /**
     * Issues a fresh token and re-enables the feed, invalidating the previous URL.
     *
     * @param newToken new opaque feed token
     */
    public void regenerate(String newToken) {
        this.token = newToken;
        this.enabled = true;
        this.revokedAt = null;
    }

    /**
     * Disables the feed, recording when. The row and token are kept for audit,
     * but the feed no longer resolves.
     *
     * @param at disable time in UTC
     */
    public void disable(LocalDateTime at) {
        this.enabled = false;
        this.revokedAt = at;
    }
}
