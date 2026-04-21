package life.wellnara.repository;

import life.wellnara.model.ClientInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for client invitations.
 */
public interface ClientInvitationRepository extends JpaRepository<ClientInvitation, Long> {

    /**
     * Finds client invitation by token.
     *
     * @param token invitation token
     * @return found invitation or empty result
     */
    Optional<ClientInvitation> findByToken(String token);

    /**
     * Checks whether invitation for email already exists.
     *
     * @param email invited email
     * @return true if invitation exists
     */
    boolean existsByEmail(String email);
}