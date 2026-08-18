package life.wellnara.controller;

import life.wellnara.model.User;
import life.wellnara.service.calendar.AppointmentCalendarService;
import life.wellnara.service.calendar.CalendarDownload;
import life.wellnara.web.CurrentUser;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Serves the per-appointment iCalendar file behind the "Add to calendar" action.
 *
 * <p>Available to both roles: the request is authenticated by the security
 * filter chain, and participant-level access is enforced by
 * {@link AppointmentCalendarService}, which returns an empty result for a
 * non-participant so this controller answers {@code 404}.
 */
@Controller
public class AppointmentCalendarController {

    private static final MediaType CALENDAR_MEDIA_TYPE =
            MediaType.parseMediaType("text/calendar; charset=UTF-8");

    private final AppointmentCalendarService appointmentCalendarService;

    /**
     * Creates the appointment calendar controller.
     *
     * @param appointmentCalendarService service that builds the calendar download
     */
    public AppointmentCalendarController(AppointmentCalendarService appointmentCalendarService) {
        this.appointmentCalendarService = appointmentCalendarService;
    }

    /**
     * Downloads the calendar file of a scheduled appointment.
     *
     * @param appointmentId appointment identifier
     * @param currentUser   authenticated user
     * @return the {@code text/calendar} file, or {@code 404} if it is not
     *         available to this user
     */
    @GetMapping("/appointments/{appointmentId}/calendar.ics")
    public ResponseEntity<String> downloadCalendar(@PathVariable Long appointmentId,
                                                    @CurrentUser User currentUser) {
        return appointmentCalendarService.downloadFor(currentUser, appointmentId)
                .map(this::asAttachment)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<String> asAttachment(CalendarDownload download) {
        return ResponseEntity.ok()
                .contentType(CALENDAR_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + download.fileName() + "\"")
                .body(download.content());
    }
}
