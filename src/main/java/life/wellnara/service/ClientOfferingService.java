package life.wellnara.service;

import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for client access to provider offerings.
 */
@Service
public class ClientOfferingService {

    private final ProviderClientLinkRepository providerClientLinkRepository;
    private final OfferingRepository offeringRepository;

    /**
     * Creates client offering service.
     *
     * @param providerClientLinkRepository repository for provider-client links
     * @param offeringRepository repository for offerings
     */
    public ClientOfferingService(ProviderClientLinkRepository providerClientLinkRepository,
                                 OfferingRepository offeringRepository) {
        this.providerClientLinkRepository = providerClientLinkRepository;
        this.offeringRepository = offeringRepository;
    }

    /**
     * Returns active offerings of provider linked to current client.
     *
     * @param client client user
     * @return list of active offerings of client's provider
     */
    @Transactional(readOnly = true)
    public List<Offering> getOfferingsOfClientProvider(User client) {
        validateClient(client);

        ProviderClientLink providerClientLink = providerClientLinkRepository.findByClient(client)
                .orElseThrow(() -> new IllegalArgumentException("Provider link not found"));

        return offeringRepository.findAllByProviderAndActiveTrue(providerClientLink.getProvider());
    }
    
    /**
     * Returns provider linked to current client.
     *
     * @param client client user
     * @return provider linked to client
     */
    @Transactional(readOnly = true)
    public User getProviderOfClient(User client) {
        validateClient(client);

        ProviderClientLink providerClientLink = providerClientLinkRepository.findByClient(client)
                .orElseThrow(() -> new IllegalArgumentException("Provider link not found"));

        return providerClientLink.getProvider();
    }

    /**
     * Validates client role.
     *
     * @param client client user
     */
    private void validateClient(User client) {
        if (client.getRole() != UserRole.CLIENT) {
            throw new IllegalArgumentException("Only client can view provider offerings");
        }
    }
    
    @Transactional(readOnly = true)
    public Offering getOfferingOfClientProvider(User client, Long offeringId) {
        validateClient(client);

        ProviderClientLink providerClientLink = providerClientLinkRepository.findByClient(client)
                .orElseThrow(() -> new IllegalArgumentException("Provider link not found"));

        return offeringRepository.findByProviderAndId(providerClientLink.getProvider(), offeringId)
                .orElseThrow(() -> new IllegalArgumentException("Offering not found"));
    }
}