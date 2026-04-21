package life.wellnara.service;

import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service for provider client operations.
 */
@Service
public class ProviderClientService {

    private final ProviderClientLinkRepository providerClientLinkRepository;
    private final UserRepository userRepository;

    /**
     * Creates provider client service.
     *
     * @param providerClientLinkRepository repository for provider-client links
     * @param userRepository repository for users
     */
    public ProviderClientService(ProviderClientLinkRepository providerClientLinkRepository,
                                 UserRepository userRepository) {
        this.providerClientLinkRepository = providerClientLinkRepository;
        this.userRepository = userRepository;
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
     * Deletes client belonging to provider.
     *
     * @param provider provider user
     * @param clientId client identifier
     */
    @Transactional
    public void deleteClient(User provider, Long clientId) {
        validateProvider(provider);

        Optional<ProviderClientLink> linkOpt =
                providerClientLinkRepository.findByProviderAndClientId(provider, clientId);

        if (linkOpt.isEmpty()) {
            return; // чужой клиент или не существует — просто игнорируем
        }

        ProviderClientLink link = linkOpt.get();

        providerClientLinkRepository.delete(link);
        userRepository.delete(link.getClient());
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