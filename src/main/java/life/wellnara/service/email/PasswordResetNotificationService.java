package life.wellnara.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

/**
 * Builds and sends the "forgot password" email.
 *
 * <p>Turns a raw reset token into a reset link and a plain-text body, then
 * delegates delivery to {@link EmailService}. The service layer orchestrates the
 * flow; email content is not constructed in controllers.
 *
 * <p>The message is written in the language of the request that triggered it,
 * since a reset can be requested from the public page before the account language
 * is known.
 */
@Service
public class PasswordResetNotificationService {

    private final EmailService emailService;
    private final String publicBaseUrl;
    private final MessageSource messageSource;

    /**
     * Creates the password reset notification service.
     *
     * @param emailService  low-level email delivery
     * @param publicBaseUrl public application base URL used to build the reset link
     * @param messageSource resolver of localized email text
     */
    public PasswordResetNotificationService(EmailService emailService,
                                            @Value("${wellnara.public-base-url}") String publicBaseUrl,
                                            MessageSource messageSource) {
        this.emailService = emailService;
        this.publicBaseUrl = publicBaseUrl;
        this.messageSource = messageSource;
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
                msg("email.reset.subject"),
                msg("email.reset.body", resetLink));
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }
}
