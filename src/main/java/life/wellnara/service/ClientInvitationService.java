package life.wellnara.service;

import life.wellnara.model.ClientInvitation;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.ClientInvitationRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for client invitation and registration flow.
 */
@Service
public class ClientInvitationService {

    private final ClientInvitationRepository clientInvitationRepository;
    private final ProviderClientLinkRepository providerClientLinkRepository;
    private final UserRepository userRepository;

    /**
     * Creates client invitation service.
     *
     * @param clientInvitationRepository repository for client invitations
     * @param providerClientLinkRepository repository for provider-client links
     * @param userRepository repository for users
     */
    public ClientInvitationService(ClientInvitationRepository clientInvitationRepository,
                                   ProviderClientLinkRepository providerClientLinkRepository,
                                   UserRepository userRepository) {
        this.clientInvitationRepository = clientInvitationRepository;
        this.providerClientLinkRepository = providerClientLinkRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates client invitation for given provider and email.
     *
     * @param provider inviting provider
     * @param email invited client email
     * @return generated invitation token
     */
    @Transactional
    public String invite(User provider, String email) {
        validateProvider(provider);
        validateEmail(email);

        ClientInvitation invitation = new ClientInvitation(provider, email);
        clientInvitationRepository.save(invitation);

        return invitation.getToken();
    }

    /**
     * Returns invited client email by invitation token.
     *
     * @param token invitation token
     * @return email from invitation
     */
    @Transactional(readOnly = true)
    public String getEmailByToken(String token) {
        ClientInvitation invitation = clientInvitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        return invitation.getEmail();
    }

    /**
     * Registers client by invitation token.
     *
     * @param token invitation token
     * @param name client username
     * @param password client password
     * @return created client user
     */
    @Transactional
    public User register(String token, String name, String password) {
        ClientInvitation invitation = clientInvitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        User client = new User();
        client.setEmail(invitation.getEmail());
        client.setUsername(name);
        client.setPassword(password);
        client.setRole(UserRole.CLIENT);

        User savedClient = userRepository.save(client);

        ProviderClientLink providerClientLink = new ProviderClientLink(
                invitation.getProvider(),
                savedClient,
                invitation.getInvitedAt()
        );
        providerClientLinkRepository.save(providerClientLink);

        clientInvitationRepository.delete(invitation);

        return savedClient;
    }

    /**
     * Validates inviting user role.
     *
     * @param provider inviting user
     */
    private void validateProvider(User provider) {
        if (provider.getRole() != UserRole.PROVIDER) {
            throw new IllegalArgumentException("Only provider can invite client");
        }
    }

    /**
     * Validates invited client email.
     *
     * @param email invited email
     */
    private void validateEmail(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already used");
        }

        if (clientInvitationRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Invitation already exists");
        }
    }
}