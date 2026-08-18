package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.service.calendar.AppointmentCalendarLinkService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Serves the provider cabinet as five separate pages, one per section.
 *
 * <p>Each page loads only its own data through {@link ProviderPageModelAssembler}.
 * Housekeeping that used to run on every visit to the single page is now bound
 * to the pages whose correctness depends on it. Stale requests are expired both
 * on the appointments page (to keep the pending list current) and on the
 * availability page: a requested term still within its confirmation window must
 * show as busy, while an expired one must free the slot and release the hold, so
 * the free-term computation needs the expiry to have run first. Expired
 * availability periods and one-time overrides are cleaned on the availability
 * page. A scheduler is the target solution for these cleanups (out of scope here).
 */
@Controller
public class ProviderController {

    private static final String CLIENTS_VIEW = "provider-clients";
    private static final String OFFERINGS_VIEW = "provider-offerings";
    private static final String APPOINTMENTS_VIEW = "provider-appointments";
    private static final String REQUESTS_VIEW = "provider-requests";
    private static final String AVAILABILITY_VIEW = "provider-availability";
    private static final String PROFILE_VIEW = "provider-profile";

    private final ProviderCalendarService providerCalendarService;
    private final AppointmentService appointmentService;
    private final ProviderPageModelAssembler providerPageModelAssembler;
    private final CalendarFeedModelAssembler calendarFeedModelAssembler;
    private final AppointmentCalendarLinkService appointmentCalendarLinkService;

    /**
     * Creates provider controller.
     *
     * @param providerCalendarService service for provider calendar management
     * @param appointmentService service for appointment operations
     * @param providerPageModelAssembler assembler for provider page model
     * @param calendarFeedModelAssembler assembler for the calendar feed section
     * @param appointmentCalendarLinkService service for per-appointment add-to-calendar links
     */
    public ProviderController(ProviderCalendarService providerCalendarService,
                              AppointmentService appointmentService,
                              ProviderPageModelAssembler providerPageModelAssembler,
                              CalendarFeedModelAssembler calendarFeedModelAssembler,
                              AppointmentCalendarLinkService appointmentCalendarLinkService) {
        this.providerCalendarService = providerCalendarService;
        this.appointmentService = appointmentService;
        this.providerPageModelAssembler = providerPageModelAssembler;
        this.calendarFeedModelAssembler = calendarFeedModelAssembler;
        this.appointmentCalendarLinkService = appointmentCalendarLinkService;
    }

    /**
     * Redirects the cabinet root to the clients page, keeping old links,
     * bookmarks and the post-login default working.
     *
     * @return redirect to the clients page
     */
    @GetMapping("/provider")
    public String showProvider() {
        return "redirect:/provider/clients";
    }

    /**
     * Shows the clients page: the provider's clients and the invite result
     * messages carried over from the invite POST through the session.
     *
     * @param currentUser authenticated provider
     * @param session current HTTP session
     * @param model MVC model
     * @return clients page view name
     */
    @GetMapping("/provider/clients")
    public String showClients(@CurrentUser User currentUser, HttpSession session, Model model) {
        moveSessionAttributeToModel(session, model, "clientInviteSuccessMessage");
        moveSessionAttributeToModel(session, model, "clientInviteError");

        providerPageModelAssembler.populateClients(model, currentUser);

        return CLIENTS_VIEW;
    }

    /**
     * Shows the offerings page.
     *
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return offerings page view name
     */
    @GetMapping("/provider/offerings")
    public String showOfferings(@CurrentUser User currentUser, Model model) {
        providerPageModelAssembler.populateOfferings(model, currentUser);

        return OFFERINGS_VIEW;
    }

    /**
     * Shows the appointments page. Expires stale appointment requests first, so
     * the pending list reflects the current state.
     *
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return appointments page view name
     */
    @GetMapping("/provider/appointments")
    public String showAppointments(@CurrentUser User currentUser, Model model) {
        appointmentService.expireStaleAppointmentRequests();

        providerPageModelAssembler.populateAppointments(model, currentUser);
        calendarFeedModelAssembler.populateFeed(model, currentUser);
        model.addAttribute("calendarAddLinks", appointmentCalendarLinkService.scheduledLinksFor(currentUser));

        return APPOINTMENTS_VIEW;
    }

    /**
     * Shows the appointment requests page: the provider's pending requests to
     * accept or reject. Expires stale requests first, so the list is current.
     *
     * @param currentUser authenticated provider
     * @param model       MVC model
     * @return requests page view name
     */
    @GetMapping("/provider/requests")
    public String showRequests(@CurrentUser User currentUser, Model model) {
        appointmentService.expireStaleAppointmentRequests();

        providerPageModelAssembler.populateAppointments(model, currentUser);

        return REQUESTS_VIEW;
    }

    /**
     * Shows the availability page. Expires stale appointment requests and removes
     * expired availability periods and one-time overrides first, so the generated
     * terms are current: a still-pending request keeps its slot busy, while an
     * expired one frees the slot and releases the hold.
     *
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return availability page view name
     */
    @GetMapping("/provider/availability")
    public String showAvailability(@CurrentUser User currentUser, Model model) {
        appointmentService.expireStaleAppointmentRequests();
        providerCalendarService.deleteExpiredAvailabilityPeriods(currentUser);
        providerCalendarService.deleteExpiredAvailabilityOverrides(currentUser);

        providerPageModelAssembler.populateAvailability(model, currentUser);

        return AVAILABILITY_VIEW;
    }

    /**
     * Shows the profile page.
     *
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return profile page view name
     */
    @GetMapping("/provider/profile")
    public String showProfile(@CurrentUser User currentUser, Model model) {
        providerPageModelAssembler.populateProfile(model, currentUser);
        calendarFeedModelAssembler.populateFeed(model, currentUser);

        return PROFILE_VIEW;
    }

    private void moveSessionAttributeToModel(HttpSession session,
                                             Model model,
                                             String attributeName) {
        Object attributeValue = session.getAttribute(attributeName);

        if (attributeValue != null) {
            model.addAttribute(attributeName, attributeValue);
            session.removeAttribute(attributeName);
        }
    }
}
