package life.wellnara.service;

import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.ProviderClientLinkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service for provider client operations.
 */
@Service
public class ProviderClientService {

    private final ProviderClientLinkRepository providerClientLinkRepository;

    /**
     * Creates provider client service.
     *
     * @param providerClientLinkRepository repository for provider-client links
     */
    public ProviderClientService(ProviderClientLinkRepository providerClientLinkRepository) {
        this.providerClientLinkRepository = providerClientLinkRepository;
    }

    /**
     * Returns all clients of provider.
     *
     * @param provider provider user
     * @return list of provider-client links
     */
    @Transactional(readOnly = true)
    public List<ProviderClientLink> getClientsOfProvider(User provider) {
        validateProvider(provider);
        return providerClientLinkRepository.findAllByProvider(provider);
    }

    /**
     * Validates provider role.
     *
     * @param provider provider user
     */
    private void validateProvider(User provider) {
        if (provider.getRole() != UserRole.PROVIDER) {
            throw new IllegalArgumentException("Only provider can manage clients");
        }
    }
}
