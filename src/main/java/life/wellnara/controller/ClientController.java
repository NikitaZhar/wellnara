package life.wellnara.controller;

import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.ClientPackageBookingService;
import life.wellnara.service.ClientOfferingService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.service.UserProfileService;
import life.wellnara.service.calendar.AppointmentCalendarLinkService;
import life.wellnara.web.CurrentUser;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Serves the client cabinet as separate pages — offerings, appointments and
 * profile — plus a single-offering booking page.
 *
 * <p>Each page loads only its own data through {@link ClientPageModelAssembler}.
 * Thin controller: booking and profile rules live in the services; on a rejected
 * booking or a failed profile update the relevant page is re-rendered in place
 * with the error.
 */
@Controller
public class ClientController {

    private static final String OFFERINGS_VIEW = "client-offerings";
    private static final String APPOINTMENTS_VIEW = "client-appointments";
    private static final String REQUESTS_VIEW = "client-requests";
    private static final String PROFILE_VIEW = "client-profile";
    private static final String OFFERING_VIEW = "client-offering";
    private static final String APPOINTMENTS_REDIRECT = "redirect:/client/appointments";
    private static final String REQUESTS_REDIRECT = "redirect:/client/requests";

    private final ClientOfferingService clientOfferingService;
    private final AppointmentService appointmentService;
    private final ProviderCalendarService providerCalendarService;
    private final UserProfileService userProfileService;
    private final ClientPageModelAssembler clientPageModelAssembler;
    private final CalendarFeedModelAssembler calendarFeedModelAssembler;
    private final AppointmentCalendarLinkService appointmentCalendarLinkService;
    private final ClientPackageBookingService clientPackageBookingService;

    /**
     * Creates client controller.
     *
     * @param clientOfferingService          service for client access to provider offerings
     * @param appointmentService             service for appointment requests
     * @param providerCalendarService        service for provider calendar operations
     * @param userProfileService             service for user personal data
     * @param clientPageModelAssembler       assembler for client page model
     * @param calendarFeedModelAssembler     assembler for the calendar feed section
     * @param appointmentCalendarLinkService service for per-appointment add-to-calendar links
     * @param clientPackageBookingService    service coordinating package purchase with first booking
     */
    public ClientController(ClientOfferingService clientOfferingService,
                            AppointmentService appointmentService,
                            ProviderCalendarService providerCalendarService,
                            UserProfileService userProfileService,
                            ClientPageModelAssembler clientPageModelAssembler,
                            CalendarFeedModelAssembler calendarFeedModelAssembler,
                            AppointmentCalendarLinkService appointmentCalendarLinkService,
                            ClientPackageBookingService clientPackageBookingService) {
        this.clientOfferingService = clientOfferingService;
        this.appointmentService = appointmentService;
        this.providerCalendarService = providerCalendarService;
        this.userProfileService = userProfileService;
        this.clientPageModelAssembler = clientPageModelAssembler;
        this.calendarFeedModelAssembler = calendarFeedModelAssembler;
        this.appointmentCalendarLinkService = appointmentCalendarLinkService;
        this.clientPackageBookingService = clientPackageBookingService;
    }

    /**
     * Redirects the cabinet root to the offerings page, keeping old links,
     * bookmarks and the post-login default working.
     *
     * @return redirect to the offerings page
     */
    @GetMapping("/client")
    public String showClient() {
        return "redirect:/client/offerings";
    }

    /**
     * Shows the offerings page: what the client's provider offers.
     *
     * @param currentUser authenticated client
     * @param model       MVC model
     * @return offerings page view name
     */
    @GetMapping("/client/offerings")
    public String showOfferings(@CurrentUser User currentUser, Model model) {
        clientPageModelAssembler.populateOfferings(model, currentUser);

        return OFFERINGS_VIEW;
    }

    /**
     * Shows the appointments page: pending requests and provider notifications
     * plus the confirmed appointments.
     *
     * @param currentUser authenticated client
     * @param model       MVC model
     * @return appointments page view name
     */
    @GetMapping("/client/appointments")
    public String showAppointments(@CurrentUser User currentUser, Model model) {
        clientPageModelAssembler.populateAppointments(model, currentUser);
        calendarFeedModelAssembler.populateFeed(model, currentUser);
        model.addAttribute("calendarAddLinks", appointmentCalendarLinkService.scheduledLinksFor(currentUser));

        return APPOINTMENTS_VIEW;
    }

    /**
     * Shows the requests page: the client's pending requests and provider
     * notifications (accepted/rejected/completed updates).
     *
     * @param currentUser authenticated client
     * @param model       MVC model
     * @return requests page view name
     */
    @GetMapping("/client/requests")
    public String showRequests(@CurrentUser User currentUser, Model model) {
        clientPageModelAssembler.populateRequests(model, currentUser);

        return REQUESTS_VIEW;
    }

    /**
     * Shows the profile page.
     *
     * @param currentUser authenticated client
     * @param model       MVC model
     * @return profile page view name
     */
    @GetMapping("/client/profile")
    public String showProfile(@CurrentUser User currentUser, Model model) {
        clientPageModelAssembler.populateProfile(model, currentUser);
        calendarFeedModelAssembler.populateFeed(model, currentUser);

        return PROFILE_VIEW;
    }

    /**
     * Shows a single offering with its bookable calendar terms.
     *
     * @param offeringId  offering identifier
     * @param currentUser authenticated client
     * @param model       MVC model
     * @return client offering view name
     */
    @GetMapping("/client/offerings/{offeringId}")
    public String showOffering(@PathVariable Long offeringId,
                               @CurrentUser User currentUser,
                               Model model) {
        clientPageModelAssembler.populateOffering(model, currentUser, offeringId);

        return OFFERING_VIEW;
    }

    /**
     * Creates an appointment request for the current client.
     *
     * <p>The selected date and time are interpreted in the provider's timezone
     * and stored in UTC. On a domain rejection the offering booking page is
     * re-rendered with the error, keeping the client on the page they booked from.
     *
     * @param providerId   provider identifier
     * @param offeringId   offering identifier
     * @param selectedDate requested date in the provider timezone
     * @param selectedTime requested time in the provider timezone
     * @param currentUser  authenticated client
     * @param model        MVC model
     * @return redirect to the appointments page on success, or the offering page with an error
     */
    @PostMapping("/client/appointments")
    public String requestAppointment(@RequestParam Long providerId,
                                     @RequestParam Long offeringId,
                                     @RequestParam LocalDate selectedDate,
                                     @RequestParam LocalTime selectedTime,
                                     @CurrentUser User currentUser,
                                     Model model) {
        try {
            User provider = clientOfferingService.getProviderOfClient(currentUser);
            ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);

            LocalDateTime startDateTimeUtc = LocalDateTime
                    .of(selectedDate, selectedTime)
                    .atZone(providerZone)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();

            appointmentService.requestAppointment(
                    currentUser,
                    providerId,
                    offeringId,
                    startDateTimeUtc
            );

            return REQUESTS_REDIRECT;
        } catch (IllegalArgumentException exception) {
            clientPageModelAssembler.populateOffering(model, currentUser, offeringId);
            model.addAttribute("appointmentError", exception.getMessage());
            return OFFERING_VIEW;
        }
    }

    /**
     * Requests a package of the offering together with the first session's time:
     * the whole price is reserved on the client's wallet and the request is sent to
     * the provider for approval. The selected date and time are interpreted in the
     * provider's timezone and stored in UTC. On a domain rejection the offering page
     * is re-rendered in place with the error.
     *
     * @param offeringId   offering to buy sessions of
     * @param sessions     number of sessions in the package (1 up to the offering's maximum)
     * @param selectedDate first-session date in the provider timezone
     * @param selectedTime first-session time in the provider timezone
     * @param currentUser  authenticated client
     * @param model        MVC model
     * @return redirect to the requests page on success, or the offering page with an error
     */
    @PostMapping("/client/packages")
    public String requestPackage(@RequestParam Long offeringId,
                                 @RequestParam int sessions,
                                 @RequestParam LocalDate selectedDate,
                                 @RequestParam LocalTime selectedTime,
                                 @CurrentUser User currentUser,
                                 Model model) {
        try {
            User provider = clientOfferingService.getProviderOfClient(currentUser);
            ZoneId providerZone = providerCalendarService.getProviderTimezone(provider);

            LocalDateTime startDateTimeUtc = LocalDateTime
                    .of(selectedDate, selectedTime)
                    .atZone(providerZone)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toLocalDateTime();

            clientPackageBookingService.requestPackage(
                    currentUser, offeringId, sessions, selectedDate, selectedTime, startDateTimeUtc);

            return REQUESTS_REDIRECT;
        } catch (IllegalArgumentException exception) {
            clientPageModelAssembler.populateOffering(model, currentUser, offeringId);
            model.addAttribute("appointmentError", exception.getMessage());
            return OFFERING_VIEW;
        }
    }

    /**
     * Withdraws a pending package request the client made; the held price is
     * released back to their wallet.
     *
     * @param packageId   requested package identifier
     * @param currentUser authenticated client
     * @return redirect to the requests page
     */
    @PostMapping("/client/packages/{packageId}/cancel-request")
    public String cancelPendingPackage(@PathVariable Long packageId,
                                       @CurrentUser User currentUser) {
        try {
            clientPackageBookingService.cancelPackageRequest(currentUser, packageId);
        } catch (IllegalArgumentException | IllegalStateException alreadyHandled) {
            // Not found, not the client's, or already processed (e.g. double submit).
        }
        return REQUESTS_REDIRECT;
    }

    /**
     * Updates the client profile and, optionally, the client password.
     *
     * <p>When any of the password fields is filled in, the current password is
     * verified and the new password confirmed <em>before</em> anything is saved,
     * so a wrong current password never leaves the name/phone update applied
     * without the password change (or vice versa).
     *
     * @param firstName          new first name
     * @param lastName           new last name
     * @param phone              new phone number, optional
     * @param currentPassword    current password, required only when changing the password
     * @param newPassword        new password, required only when changing the password
     * @param confirmNewPassword repeated new password, required only when changing the password
     * @param currentUser        authenticated client
     * @param model              MVC model
     * @return redirect to the profile page, or the profile page re-rendered with an error
     */
    @PostMapping("/client/profile")
    public String updateClientProfile(@RequestParam String firstName,
                                      @RequestParam String lastName,
                                      @RequestParam(required = false) String phone,
                                      @RequestParam(required = false) String currentPassword,
                                      @RequestParam(required = false) String newPassword,
                                      @RequestParam(required = false) String confirmNewPassword,
                                      @CurrentUser User currentUser,
                                      Model model) {
        try {
            userProfileService.updateProfileAndPassword(currentUser,
                    firstName, lastName, phone,
                    currentPassword, newPassword, confirmNewPassword);

            return "redirect:/client/profile?profileUpdated";
        } catch (IllegalArgumentException exception) {
            clientPageModelAssembler.populateProfile(model, currentUser);
            calendarFeedModelAssembler.populateFeed(model, currentUser);
            model.addAttribute("profileFirstName", firstName);
            model.addAttribute("profileLastName", lastName);
            model.addAttribute("profilePhone", phone);
            model.addAttribute("profileError", exception.getMessage());
            return PROFILE_VIEW;
        }
    }

    /**
     * Acknowledges a provider-cancelled or completed appointment and removes it
     * from the client's notifications.
     *
     * @param appointmentId appointment identifier
     * @param currentUser   authenticated client
     * @return redirect to the appointments page
     */
    @PostMapping("/client/appointments/{appointmentId}/acknowledge")
    public String acknowledgeAppointment(@PathVariable Long appointmentId,
                                         @CurrentUser User currentUser) {
        appointmentService.acknowledgeRejectedAppointment(currentUser, appointmentId);

        return REQUESTS_REDIRECT;
    }

    /**
     * Cancels a pending (not yet accepted) appointment request.
     *
     * @param appointmentId appointment identifier
     * @param currentUser   authenticated client
     * @return redirect to the appointments page
     */
    @PostMapping("/client/appointments/{appointmentId}/cancel-request")
    public String cancelPendingAppointment(@PathVariable Long appointmentId,
                                           @CurrentUser User currentUser) {
        appointmentService.cancelPendingAppointmentByClient(currentUser, appointmentId);

        return REQUESTS_REDIRECT;
    }

    /**
     * Cancels a scheduled appointment.
     *
     * @param appointmentId appointment identifier
     * @param currentUser   authenticated client
     * @return redirect to the appointments page
     */
    @PostMapping("/client/appointments/{appointmentId}/cancel")
    public String cancelScheduledAppointment(@PathVariable Long appointmentId,
                                             @CurrentUser User currentUser) {
        appointmentService.cancelScheduledAppointmentByClient(currentUser, appointmentId);

        return APPOINTMENTS_REDIRECT;
    }

    /**
     * Reschedules a scheduled appointment: releases the current booking (the payment
     * is kept) and sends the client to that offering's booking screen to pick a new
     * time. Offered only when the appointment is far enough ahead; the template hides
     * the action otherwise and the service enforces the same window.
     *
     * @param appointmentId appointment identifier
     * @param currentUser   authenticated client
     * @return redirect to the offering booking page for the freed appointment
     */
    @PostMapping("/client/appointments/{appointmentId}/reschedule")
    public String rescheduleScheduledAppointment(@PathVariable Long appointmentId,
                                                 @CurrentUser User currentUser) {
        Long offeringId = appointmentService.rescheduleScheduledAppointmentByClient(currentUser, appointmentId);

        return "redirect:/client/offerings/" + offeringId;
    }
}
