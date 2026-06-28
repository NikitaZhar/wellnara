package life.wellnara.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds and sends registration invitation emails.
 * <p>
 * Owns the invitation message: it turns a registration token into a registration link
 * and a localized email body, then delegates delivery to {@link EmailService}.
 * Controllers orchestrate the flow but no longer construct email content themselves.
 */
@Service
public class InvitationNotificationService {

    private final EmailService emailService;
    private final String publicBaseUrl;

    /**
     * Creates the invitation notification service.
     *
     * @param emailService  low-level email delivery
     * @param publicBaseUrl public application base URL used to build registration links
     */
    public InvitationNotificationService(EmailService emailService,
                                         @Value("${wellnara.public-base-url}") String publicBaseUrl) {
        this.emailService = emailService;
        this.publicBaseUrl = publicBaseUrl;
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
                "Wellnara provider invitation",
                buildProviderBody(registrationLink));
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
                "Wellnara — приглашение для регистрации",
                buildClientBody(registrationLink));
    }

    private String buildProviderBody(String registrationLink) {
        return """
                You have been invited to register as a provider on Wellnara.

                Please use the following link to complete your registration:

                %s

                If you did not expect this invitation, you can ignore this email.
                """.formatted(registrationLink);
    }

    private String buildClientBody(String registrationLink) {
        return """
                Вас пригласили зарегистрироваться на платформе Wellnara.

                Для завершения регистрации перейдите по ссылке:

                %s

                Если вы не ожидали это приглашение — просто проигнорируйте письмо.
                """.formatted(registrationLink);
    }
}
