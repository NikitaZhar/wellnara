package life.wellnara.controller;

import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.AuthService;
import life.wellnara.service.ClientOfferingService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.service.UserProfileService;
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
    private static final String PROFILE_VIEW = "client-profile";
    private static final String OFFERING_VIEW = "client-offering";
    private static final String APPOINTMENTS_REDIRECT = "redirect:/client/appointments";

    private final ClientOfferingService clientOfferingService;
    private final AppointmentService appointmentService;
    private final ProviderCalendarService providerCalendarService;
    private final UserProfileService userProfileService;
    private final AuthService authService;
    private final ClientPageModelAssembler clientPageModelAssembler;

    /**
     * Creates client controller.
     *
     * @param clientOfferingService    service for client access to provider offerings
     * @param appointmentService       service for appointment requests
     * @param providerCalendarService  service for provider calendar operations
     * @param userProfileService       service for user personal data
     * @param authService              service for password verification and change
     * @param clientPageModelAssembler assembler for client page model
     */
    public ClientController(ClientOfferingService clientOfferingService,
                            AppointmentService appointmentService,
                            ProviderCalendarService providerCalendarService,
                            UserProfileService userProfileService,
                            AuthService authService,
                            ClientPageModelAssembler clientPageModelAssembler) {
        this.clientOfferingService = clientOfferingService;
        this.appointmentService = appointmentService;
        this.providerCalendarService = providerCalendarService;
        this.userProfileService = userProfileService;
        this.authService = authService;
        this.clientPageModelAssembler = clientPageModelAssembler;
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

        return APPOINTMENTS_VIEW;
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

            return APPOINTMENTS_REDIRECT;
        } catch (IllegalArgumentException exception) {
            clientPageModelAssembler.populateOffering(model, currentUser, offeringId);
            model.addAttribute("appointmentError", exception.getMessage());
            return OFFERING_VIEW;
        }
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
            boolean passwordChangeRequested =
                    hasText(currentPassword) || hasText(newPassword) || hasText(confirmNewPassword);

            if (passwordChangeRequested) {
                if (!authService.verifyPassword(currentUser, currentPassword)) {
                    throw new IllegalArgumentException("Current password is incorrect");
                }
                if (!newPassword.equals(confirmNewPassword)) {
                    throw new IllegalArgumentException("New passwords do not match");
                }
            }

            userProfileService.updateProfile(currentUser, firstName, lastName, phone);

            if (passwordChangeRequested) {
                authService.changePassword(currentUser, newPassword);
            }

            return "redirect:/client/profile?profileUpdated";
        } catch (IllegalArgumentException exception) {
            clientPageModelAssembler.populateProfile(model, currentUser);
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

        return APPOINTMENTS_REDIRECT;
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

        return APPOINTMENTS_REDIRECT;
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
