package life.wellnara.dto;

/**
 * One-click "add to calendar" links for a single appointment.
 *
 * <p>Unlike the ICS download and the subscription feed, these open a pre-filled
 * new-event window in the web calendar itself, with all event data carried in
 * the URL. They therefore work without the server being reachable by the
 * calendar provider — handy in local and staging environments.
 *
 * @param google absolute Google Calendar "create event" link (pre-filled)
 * @param outlook absolute Outlook Calendar "compose event" link (pre-filled)
 */
public record AppointmentCalendarLinks(String google, String outlook) {
}
