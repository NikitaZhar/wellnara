package life.wellnara.service;

import life.wellnara.model.ClientInvitation;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.ClientInvitationRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.time.ApplicationTimeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for the client invitation and registration flow.
 */
@Service
public class ClientInvitationService {

    private final ClientInvitationRepository clientInvitationRepository;
    private final ProviderClientLinkRepository providerClientLinkRepository;
    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final ApplicationTimeService applicationTimeService;
    private final long ttlDays;

    /**
     * Creates the client invitation service.
     *
     * @param clientInvitationRepository   repository for client invitations
     * @param providerClientLinkRepository repository for provider-client links
     * @param userRepository               repository for users
     * @param userProfileService           service for user personal data
     * @param applicationTimeService       source of current application time (UTC)
     * @param ttlDays                      number of days an invitation stays valid
     */
    public ClientInvitationService(ClientInvitationRepository clientInvitationRepository,
                                   ProviderClientLinkRepository providerClientLinkRepository,
                                   UserRepository userRepository,
                                   UserProfileService userProfileService,
                                   ApplicationTimeService applicationTimeService,
                                   @Value("${wellnara.invitation.ttl-days:7}") long ttlDays) {
        this.clientInvitationRepository = clientInvitationRepository;
        this.providerClientLinkRepository = providerClientLinkRepository;
        this.userRepository = userRepository;
        this.userProfileService = userProfileService;
        this.applicationTimeService = applicationTimeService;
        this.ttlDays = ttlDays;
    }

    /**
     * Creates a client invitation for the given provider and email.
     * An existing expired invitation for the same email is replaced; an active one is rejected.
     *
     * @param provider inviting provider
     * @param email    invited client email
     * @return generated invitation token
     */
    @Transactional
    public String invite(User provider, String email) {
        requireProviderRole(provider);
        requireEmailNotRegistered(email);

        LocalDateTime now = applicationTimeService.currentUtcDateTime();
        discardExistingExpiredOrReject(email, now);

        ClientInvitation invitation =
                new ClientInvitation(provider, email, now, now.plusDays(ttlDays));
        clientInvitationRepository.save(invitation);

        return invitation.getToken();
    }

    /**
     * Returns the invited client email by invitation token.
     *
     * @param token invitation token
     * @return email from the invitation
     */
    @Transactional(readOnly = true)
    public String getEmailByToken(String token) {
        return requireValidInvitation(token).getEmail();
    }

    /**
     * Registers a client by invitation token.
     *
     * @param token     invitation token
     * @param name      client login nickname
     * @param password  client password
     * @param firstName client first name
     * @param lastName  client last name
     * @param phone     client phone number, optional (may be null or blank)
     * @return created client user
     */
    @Transactional
    public User register(String token,
                         String name,
                         String password,
                         String firstName,
                         String lastName,
                         String phone) {
        ClientInvitation invitation = requireValidInvitation(token);

        User client = new User();
        client.setEmail(invitation.getEmail());
        client.setUsername(name);
        client.setPassword(password);
        client.setRole(UserRole.CLIENT);

        User savedClient = userRepository.save(client);
        userProfileService.createProfile(savedClient, firstName, lastName, phone);

        ProviderClientLink providerClientLink = new ProviderClientLink(
                invitation.getProvider(),
                savedClient,
                invitation.getInvitedAt());
        providerClientLinkRepository.save(providerClientLink);

        clientInvitationRepository.delete(invitation);

        return savedClient;
    }

    private void requireProviderRole(User provider) {
        if (provider.getRole() != UserRole.PROVIDER) {
            throw new IllegalArgumentException("Only provider can invite client");
        }
    }

    private void requireEmailNotRegistered(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already used");
        }
    }

    private void discardExistingExpiredOrReject(String email, LocalDateTime now) {
        clientInvitationRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.isExpired(now)) {
                throw new IllegalArgumentException("Invitation already exists");
            }
            clientInvitationRepository.delete(existing);
        });
    }

    private ClientInvitation requireValidInvitation(String token) {
        ClientInvitation invitation = clientInvitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (invitation.isExpired(applicationTimeService.currentUtcDateTime())) {
            throw new IllegalArgumentException("Invitation has expired");
        }

        return invitation;
    }
}

