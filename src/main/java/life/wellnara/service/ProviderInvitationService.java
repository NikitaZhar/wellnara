package life.wellnara.service;

import life.wellnara.model.ProviderInvitation;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.ProviderInvitationRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.time.ApplicationTimeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Service for the provider invitation and registration flow.
 */
@Service
public class ProviderInvitationService {

    private final ProviderInvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final UserProfileService userProfileService;
    private final ApplicationTimeService applicationTimeService;
    private final long ttlDays;

    /**
     * Creates the provider invitation service.
     *
     * @param invitationRepository   repository for provider invitations
     * @param userRepository         repository for users
     * @param userProfileService     service for user personal data
     * @param applicationTimeService source of current application time (UTC)
     * @param ttlDays                number of days an invitation stays valid
     */
    public ProviderInvitationService(ProviderInvitationRepository invitationRepository,
                                     UserRepository userRepository,
                                     UserProfileService userProfileService,
                                     ApplicationTimeService applicationTimeService,
                                     @Value("${wellnara.invitation.ttl-days:7}") long ttlDays) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.userProfileService = userProfileService;
        this.applicationTimeService = applicationTimeService;
        this.ttlDays = ttlDays;
    }

    /**
     * Creates a provider invitation for the given email.
     * An existing expired invitation for the same email is replaced; an active one is rejected.
     *
     * @param email invited provider email
     * @return generated invitation token
     */
    @Transactional
    public String invite(String email) {
        requireEmailNotRegistered(email);

        LocalDateTime now = applicationTimeService.currentUtcDateTime();
        discardExistingExpiredOrReject(email, now);

        ProviderInvitation invitation = new ProviderInvitation(email, now.plusDays(ttlDays));
        invitationRepository.save(invitation);

        return invitation.getToken();
    }

    /**
     * Returns the invited provider email by invitation token.
     *
     * @param token invitation token
     * @return email from the invitation
     */
    @Transactional(readOnly = true)
    public String getEmailByToken(String token) {
        return requireValidInvitation(token).getEmail();
    }

    /**
     * Registers a provider by invitation token.
     *
     * @param token     invitation token
     * @param name      provider login nickname
     * @param password  provider password
     * @param firstName provider first name
     * @param lastName  provider last name
     * @param phone     provider phone number, optional (may be null or blank)
     * @return created provider user
     */
    @Transactional
    public User register(String token,
                         String name,
                         String password,
                         String firstName,
                         String lastName,
                         String phone) {
        ProviderInvitation invitation = requireValidInvitation(token);

        User user = new User();
        user.setEmail(invitation.getEmail());
        user.setUsername(name);
        user.setPassword(password);
        user.setRole(UserRole.PROVIDER);

        User savedUser = userRepository.save(user);
        userProfileService.createProfile(savedUser, firstName, lastName, phone);
        invitationRepository.delete(invitation);

        return savedUser;
    }

    private void requireEmailNotRegistered(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already used");
        }
    }

    private void discardExistingExpiredOrReject(String email, LocalDateTime now) {
        invitationRepository.findByEmail(email).ifPresent(existing -> {
            if (!existing.isExpired(now)) {
                throw new IllegalArgumentException("Invitation already exists");
            }
            invitationRepository.delete(existing);
        });
    }

    private ProviderInvitation requireValidInvitation(String token) {
        ProviderInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        if (invitation.isExpired(applicationTimeService.currentUtcDateTime())) {
            throw new IllegalArgumentException("Invitation has expired");
        }

        return invitation;
    }
}

