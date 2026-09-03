package life.wellnara.model;

/**
 * External calendar a user has chosen for the per-appointment "add to calendar"
 * action.
 *
 * <p>Each value maps to how the appointment is handed to that calendar:
 * {@link #GOOGLE} and {@link #OUTLOOK} open a pre-filled new-event window via an
 * absolute web link, while {@link #APPLE} downloads the appointment's {@code .ics}
 * file (Apple Calendar has no web "add event" URL). {@code null} on the user means
 * no calendar is chosen yet, and the appointments page prompts the user to pick one.
 */
public enum CalendarProvider {

    /** Google Calendar — pre-filled create-event web link. */
    GOOGLE,

    /** Outlook Calendar — pre-filled compose-event web link. */
    OUTLOOK,

    /** Apple Calendar — per-appointment {@code .ics} download. */
    APPLE
}
