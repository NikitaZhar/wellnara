package life.wellnara.service;

import life.wellnara.model.ProviderInvitation;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.ProviderInvitationRepository;
import life.wellnara.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for provider invitation and registration flow.
 */
@Service
public class ProviderInvitationService {

    private final ProviderInvitationRepository invitationRepository;
    private final UserRepository userRepository;

    /**
     * Creates provider invitation service.
     *
     * @param invitationRepository repository for provider invitations
     * @param userRepository repository for users
     */
    public ProviderInvitationService(ProviderInvitationRepository invitationRepository,
                                     UserRepository userRepository) {
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
    }

    /**
     * Creates provider invitation for given email.
     *
     * @param email invited provider email
     * @return generated invitation token
     */
    @Transactional
    public String invite(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already used");
        }

        if (invitationRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Invitation already exists");
        }

        ProviderInvitation invitation = new ProviderInvitation(email);
        invitationRepository.save(invitation);

        return invitation.getToken();
    }

    /**
     * Returns invited provider email by invitation token.
     *
     * @param token invitation token
     * @return email from invitation
     */
    @Transactional(readOnly = true)
    public String getEmailByToken(String token) {
        ProviderInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        return invitation.getEmail();
    }

    /**
     * Registers provider by invitation token.
     *
     * @param token invitation token
     * @param name provider username
     * @param password provider password
     * @return created provider user
     */
    @Transactional
    public User register(String token, String name, String password) {
        ProviderInvitation invitation = invitationRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid token"));

        User user = new User();
        user.setEmail(invitation.getEmail());
        user.setUsername(name);
        user.setPassword(password);
        user.setRole(UserRole.PROVIDER);

        User savedUser = userRepository.save(user);
        invitationRepository.delete(invitation);

        return savedUser;
    }
}