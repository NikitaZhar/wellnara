package life.wellnara.service;

import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.OfferingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for offering management.
 */
@Service
public class OfferingService {

    private final OfferingRepository offeringRepository;

    /**
     * Creates offering service.
     *
     * @param offeringRepository repository for offerings
     */
    public OfferingService(OfferingRepository offeringRepository) {
        this.offeringRepository = offeringRepository;
    }

    /**
     * Creates offering for provider.
     *
     * @param provider provider owner
     * @param name offering name
     * @param description offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     */
    @Transactional
    public void createOffering(User provider,
                               String name,
                               String description,
                               BigDecimal pricePerSession,
                               Integer durationMinutes) {
        validateProvider(provider);

        Offering offering = new Offering(
                provider,
                name,
                description,
                pricePerSession,
                durationMinutes
        );

        offeringRepository.save(offering);
    }

    /**
     * Returns all offerings of provider.
     *
     * @param provider provider owner
     * @return list of offerings
     */
    @Transactional(readOnly = true)
    public List<Offering> getOfferingsOfProvider(User provider) {
        validateProvider(provider);
        return offeringRepository.findAllByProvider(provider);
    }

    /**
     * Returns offering owned by provider.
     *
     * @param provider provider owner
     * @param offeringId offering identifier
     * @return offering owned by provider
     */
    @Transactional(readOnly = true)
    public Offering getOfferingOfProvider(User provider, Long offeringId) {
        validateProvider(provider);
        return getOwnedOffering(provider, offeringId);
    }

    /**
     * Updates offering owned by provider.
     *
     * @param provider provider owner
     * @param offeringId offering identifier
     * @param name offering name
     * @param description offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     */
    @Transactional
    public void updateOffering(User provider,
                               Long offeringId,
                               String name,
                               String description,
                               BigDecimal pricePerSession,
                               Integer durationMinutes) {
        validateProvider(provider);

        Offering offering = getOwnedOffering(provider, offeringId);
        offering.setName(name);
        offering.setDescription(description);
        offering.setPricePerSession(pricePerSession);
        offering.setDurationMinutes(durationMinutes);
    }

    /**
     * Returns offering owned by provider.
     *
     * @param provider provider owner
     * @param offeringId offering identifier
     * @return offering
     */
    private Offering getOwnedOffering(User provider, Long offeringId) {
        return offeringRepository.findByProviderAndId(provider, offeringId)
                .orElseThrow(() -> new IllegalArgumentException("Offering not found"));
    }

    /**
     * Validates provider role.
     *
     * @param provider provider user
     */
    private void validateProvider(User provider) {
        if (provider.getRole() != UserRole.PROVIDER) {
            throw new IllegalArgumentException("Only provider can manage offerings");
        }
    }
}