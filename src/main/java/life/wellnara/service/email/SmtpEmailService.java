package life.wellnara.service.email;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Sends emails through the configured SMTP provider.
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final String ENCODING = StandardCharsets.UTF_8.name();

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

    /**
     * Sends a plain text email with an iCalendar document attached.
     *
     * <p>The message is multipart: a plain text body plus a {@code text/calendar}
     * attachment whose content type carries the iCalendar method, which is what
     * lets the recipient's calendar client add or cancel the event.
     *
     * @param recipient recipient email address
     * @param subject   email subject
     * @param body      plain text email body
     * @param calendar  calendar document to attach
     * @throws IllegalStateException if the message cannot be assembled
     */
    @Override
    public void sendCalendarEmail(String recipient, String subject, String body, CalendarAttachment calendar) {
        MimeMessage message = mailSender.createMimeMessage();

        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, ENCODING);
            helper.setFrom(senderAddress);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body, false);
            helper.addAttachment(
                    calendar.fileName(),
                    new ByteArrayResource(calendar.content().getBytes(StandardCharsets.UTF_8)),
                    "text/calendar; charset=" + ENCODING + "; method=" + calendar.method().name());
        } catch (MessagingException exception) {
            throw new IllegalStateException("Failed to assemble calendar email", exception);
        }

        mailSender.send(message);
    }
}