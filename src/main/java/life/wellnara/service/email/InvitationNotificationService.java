package life.wellnara.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/**
 * Builds and sends registration invitation emails.
 * <p>
 * Owns the invitation message: it turns a registration token into a registration link
 * and a localized email body, then delegates delivery to {@link EmailService}.
 * Controllers orchestrate the flow but no longer construct email content themselves.
 *
 * <p>The invitation is written in the language of the request that triggered it
 * (the inviter's active locale), since the recipient has no account and therefore
 * no stored language yet.
 */
@Service
public class InvitationNotificationService {

    private final EmailService emailService;
    private final String publicBaseUrl;
    private final MessageSource messageSource;

    /**
     * Creates the invitation notification service.
     *
     * @param emailService  low-level email delivery
     * @param publicBaseUrl public application base URL used to build registration links
     * @param messageSource resolver of localized email text
     */
    public InvitationNotificationService(EmailService emailService,
                                         @Value("${wellnara.public-base-url}") String publicBaseUrl,
                                         MessageSource messageSource) {
        this.emailService = emailService;
        this.publicBaseUrl = publicBaseUrl;
        this.messageSource = messageSource;
    }

    /**
     * Sends a provider registration invitation.
     *
     * @param recipientEmail invited provider email
     * @param token          registration token
     */
    public void sendProviderInvitation(String recipientEmail, String token) {
        String registrationLink = publicBaseUrl + "/provider/register?token=" + token;
        emailService.sendPlainTextEmail(
                recipientEmail,
                msg("email.invite.provider.subject"),
                msg("email.invite.provider.body", registrationLink));
    }

    /**
     * Sends a client registration invitation.
     *
     * @param recipientEmail invited client email
     * @param token          registration token
     */
    public void sendClientInvitation(String recipientEmail, String token) {
        String registrationLink = publicBaseUrl + "/client/register?token=" + token;
        emailService.sendPlainTextEmail(
                recipientEmail,
                msg("email.invite.client.subject"),
                msg("email.invite.client.body", registrationLink));
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
