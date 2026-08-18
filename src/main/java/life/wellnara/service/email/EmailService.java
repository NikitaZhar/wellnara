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

    /**
     * Sends a plain text email with an iCalendar document attached.
     *
     * <p>Used to mirror an appointment into the recipient's calendar: the body
     * carries the human-readable details and the attachment lets the calendar
     * client add or cancel the event.
     *
     * @param recipient recipient email address
     * @param subject   email subject
     * @param body      plain text email body
     * @param calendar  calendar document to attach
     */
    void sendCalendarEmail(String recipient, String subject, String body, CalendarAttachment calendar);
}