package life.wellnara.service.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends emails through the configured SMTP provider.
 */
@Service
public class SmtpEmailService implements EmailService {

    private final JavaMailSender mailSender;
    private final String senderAddress;

    /**
     * Creates SMTP email service.
     *
     * @param mailSender Spring mail sender
     * @param senderAddress configured sender email address
     */
    public SmtpEmailService(JavaMailSender mailSender,
                            @Value("${wellnara.mail.from}") String senderAddress) {
        this.mailSender = mailSender;
        this.senderAddress = senderAddress;
    }

    /**
     * Sends a plain text email.
     *
     * @param recipient recipient email address
     * @param subject email subject
     * @param body email body
     */
    @Override
    public void sendPlainTextEmail(String recipient, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(senderAddress);
        message.setTo(recipient);
        message.setSubject(subject);
        message.setText(body);

        mailSender.send(message);
    }
}