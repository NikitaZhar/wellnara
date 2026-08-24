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
 * A single-use password reset token issued for the "forgot password" flow.
 *
 * <p>Only the token <em>hash</em> is persisted; the raw token is sent in the
 * reset link and never stored. A token is valid until {@code expiresAt} and only
 * while {@code usedAt} is {@code null}; consuming it stamps {@code usedAt}.
 */
@Entity
@Table(name = "password_reset_tokens")
public class PasswordResetToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    @Column
    private LocalDateTime usedAt;

    /**
     * Required by JPA.
     */
    protected PasswordResetToken() {
    }

    /**
     * Creates an unused reset token.
     *
     * @param user      token owner
     * @param tokenHash hash of the raw token (the raw token is emailed, not stored)
     * @param createdAt creation time in UTC
     * @param expiresAt expiry time in UTC
     */
    public PasswordResetToken(User user, String tokenHash, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.user = user;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /**
     * Whether the token has already been consumed.
     *
     * @return true if the token was used
     */
    public boolean isUsed() {
        return usedAt != null;
    }

    /**
     * Whether the token is expired at the given moment.
     *
     * @param now current time in UTC
     * @return true if {@code now} is at or after the expiry
     */
    public boolean isExpired(LocalDateTime now) {
        return !now.isBefore(expiresAt);
    }

    /**
     * Whether the token can still be used at the given moment.
     *
     * @param now current time in UTC
     * @return true if the token is neither used nor expired
     */
    public boolean isUsable(LocalDateTime now) {
        return !isUsed() && !isExpired(now);
    }

    /**
     * Marks the token as consumed.
     *
     * @param usedAt consumption time in UTC
     */
    public void markUsed(LocalDateTime usedAt) {
        this.usedAt = usedAt;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getUsedAt() {
        return usedAt;
    }
}
