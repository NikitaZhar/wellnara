package life.wellnara.service.email;

/**
 * Sends application emails.
 */
public interface EmailService {

    /**
     * Sends plain text email.
     *
     * @param recipient recipient email address
     * @param subject email subject
     * @param body email body
     */
    void sendPlainTextEmail(String recipient, String subject, String body);
}