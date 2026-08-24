package life.wellnara.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Builds and sends the "forgot password" email.
 *
 * <p>Turns a raw reset token into a reset link and a plain-text body, then
 * delegates delivery to {@link EmailService}. The service layer orchestrates the
 * flow; email content is not constructed in controllers.
 */
@Service
public class PasswordResetNotificationService {

    private final EmailService emailService;
    private final String publicBaseUrl;

    /**
     * Creates the password reset notification service.
     *
     * @param emailService  low-level email delivery
     * @param publicBaseUrl public application base URL used to build the reset link
     */
    public PasswordResetNotificationService(EmailService emailService,
                                            @Value("${wellnara.public-base-url}") String publicBaseUrl) {
        this.emailService = emailService;
        this.publicBaseUrl = publicBaseUrl;
    }

    /**
     * Sends a password reset link to the given address.
     *
     * @param recipientEmail account email
     * @param rawToken       raw (unhashed) reset token to place in the link
     */
    public void sendResetLink(String recipientEmail, String rawToken) {
        String resetLink = publicBaseUrl + "/auth/reset-password?token=" + rawToken;
        emailService.sendPlainTextEmail(
                recipientEmail,
                "Wellnara password reset",
                buildBody(resetLink));
    }

    private String buildBody(String resetLink) {
        return """
                We received a request to reset your Wellnara password.

                Use the following link to choose a new password (valid for a limited time):

                %s

                If you did not request this, you can safely ignore this email — your
                password will not be changed.
                """.formatted(resetLink);
    }
}
