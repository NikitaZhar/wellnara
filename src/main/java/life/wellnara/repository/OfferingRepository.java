package life.wellnara.repository;

import life.wellnara.model.Offering;
import life.wellnara.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for offerings.
 */
public interface OfferingRepository extends JpaRepository<Offering, Long> {

    /**
     * Returns all offerings of provider.
     *
     * @param provider provider owner
     * @return list of offerings
     */
    List<Offering> findAllByProvider(User provider);

    /**
     * Returns all active offerings of provider.
     *
     * @param provider provider owner
     * @return list of active offerings
     */
    List<Offering> findAllByProviderAndActiveTrue(User provider);

    /**
     * Finds offering by provider and offering id.
     *
     * @param provider provider owner
     * @param offeringId offering identifier
     * @return found offering or empty result
     */
    Optional<Offering> findByProviderAndId(User provider, Long offeringId);
}