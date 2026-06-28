package life.wellnara.repository;

import life.wellnara.model.ClientInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for client invitations.
 */
public interface ClientInvitationRepository extends JpaRepository<ClientInvitation, Long> {

    /**
     * Finds a client invitation by token.
     *
     * @param token invitation token
     * @return found invitation or empty result
     */
    Optional<ClientInvitation> findByToken(String token);

    /**
     * Finds a client invitation by invited email.
     *
     * @param email invited email
     * @return found invitation or empty result
     */
    Optional<ClientInvitation> findByEmail(String email);

    /**
     * Checks whether an invitation for the given email exists.
     *
     * @param email invited email
     * @return true if an invitation exists
     */
    boolean existsByEmail(String email);
}
