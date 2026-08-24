package life.wellnara.repository;

import life.wellnara.model.PasswordResetToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for password reset tokens.
 */
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    /**
     * Finds a token by its stored hash.
     *
     * @param tokenHash hash of the raw token
     * @return found token or empty result
     */
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    /**
     * Returns the user's not-yet-consumed tokens, newest first. Used to enforce the
     * request cool-down and to invalidate earlier links when a new one is issued.
     *
     * @param userId token owner id
     * @return unused tokens ordered by creation time descending
     */
    List<PasswordResetToken> findAllByUser_IdAndUsedAtIsNullOrderByCreatedAtDesc(Long userId);
}
