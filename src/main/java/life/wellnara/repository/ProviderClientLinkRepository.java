package life.wellnara.repository;

import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for provider-client links.
 */
public interface ProviderClientLinkRepository extends JpaRepository<ProviderClientLink, Long> {

    /**
     * Returns all client links for provider.
     *
     * @param provider provider user
     * @return list of provider-client links
     */
    List<ProviderClientLink> findAllByProvider(User provider);

    /**
     * Finds provider-client link by provider and client id.
     *
     * @param provider provider user
     * @param clientId client identifier
     * @return found link or empty result
     */
    Optional<ProviderClientLink> findByProviderAndClientId(User provider, Long clientId);
}