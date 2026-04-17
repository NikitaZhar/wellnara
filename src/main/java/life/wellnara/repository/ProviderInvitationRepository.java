package life.wellnara.repository;

import life.wellnara.model.ProviderInvitation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProviderInvitationRepository extends JpaRepository<ProviderInvitation, Long> {

    Optional<ProviderInvitation> findByToken(String token);

    boolean existsByEmail(String email);
}