package life.wellnara.controller;

import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
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
import java.util.List;

/**
 * Controller for client page access and appointment requests.
 */
@Controller
public class ClientController {

	private final ClientOfferingService clientOfferingService;
	private final AppointmentService appointmentService;
	private final ProviderCalendarService providerCalendarService;
	private final UserProfileService userProfileService;
	private final AuthService authService;

	/**
	 * Creates client controller.
	 *
	 * @param clientOfferingService service for client access to provider offerings
	 * @param appointmentService service for appointment requests
	 * @param providerCalendarService service for provider calendar operations
	 * @param userProfileService service for user personal data
	 * @param authService service for password verification and change
	 */
	public ClientController(ClientOfferingService clientOfferingService,
	        AppointmentService appointmentService,
	        ProviderCalendarService providerCalendarService,
	        UserProfileService userProfileService,
	        AuthService authService) {
	    this.clientOfferingService = clientOfferingService;
	    this.appointmentService = appointmentService;
	    this.providerCalendarService = providerCalendarService;
	    this.userProfileService = userProfileService;
	    this.authService = authService;
	}

	/**
	 * Shows client page for authenticated client user.
	 *
	 * @param currentUser authenticated client
	 * @param model MVC model
	 * @return client page view name
	 */
	@GetMapping("/client")
	public String showPage(@CurrentUser User currentUser, Model model) {
		populateClientPageModel(model, currentUser);

		return "client";
	}

	/**
	 * Creates appointment request for current client.
	 *
	 * @param providerId provider identifier
	 * @param offeringId offering identifier
	 * @param selectedDate requested date in provider timezone
	 * @param selectedTime requested time in provider timezone
	 * @param currentUser authenticated client
	 * @param model MVC model
	 * @return redirect to client page on success or client page with error
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

	        return "redirect:/client";

	    } catch (IllegalArgumentException exception) {
	        populateClientPageModel(model, currentUser);
	        model.addAttribute("appointmentError", exception.getMessage());

	        return "client";
	    }
	}
	/**
	 * Adds client page data to model.
	 *
	 * @param model MVC model
	 * @param client authenticated client
	 */
	private void populateClientPageModel(Model model, User client) {
	    model.addAttribute("clientName", userProfileService.resolveDisplayName(client));
	    model.addAttribute("offerings", clientOfferingService.getOfferingsOfClientProvider(client));
	    model.addAttribute("appointments", appointmentService.getAppointmentViewsOfClient(client));
	    model.addAttribute("confirmedAppointments",
	            appointmentService.getConfirmedAppointmentViewsOfClient(client));

	    UserProfile profile = userProfileService.findProfile(client).orElse(null);
	    model.addAttribute("clientLogin", client.getUsername());
	    model.addAttribute("clientEmail", client.getEmail());
	    model.addAttribute("profileFirstName", profile != null ? profile.getFirstName() : "");
	    model.addAttribute("profileLastName", profile != null ? profile.getLastName() : "");
	    model.addAttribute("profilePhone", profile != null ? profile.getPhone() : "");
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
	 * @return redirect to the client profile tab, or the client page with an error
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

	        return "redirect:/client?section=profile&profileUpdated";
	    } catch (IllegalArgumentException exception) {
	        populateClientPageModel(model, currentUser);
	        model.addAttribute("profileFirstName", firstName);
	        model.addAttribute("profileLastName", lastName);
	        model.addAttribute("profilePhone", phone);
	        model.addAttribute("profileError", exception.getMessage());
	        return "client";
	    }
	}

	private boolean hasText(String value) {
	    return value != null && !value.isBlank();
	}

	/**
	 * Shows a single offering with its bookable calendar terms.
	 *
	 * @param offeringId offering identifier
	 * @param currentUser authenticated client
	 * @param model MVC model
	 * @return client offering view name
	 */
	@GetMapping("/client/offerings/{offeringId}")
	public String showOffering(@PathVariable Long offeringId,
	                           @CurrentUser User currentUser,
	                           Model model) {
	    Offering offering = clientOfferingService.getOfferingOfClientProvider(currentUser, offeringId);
	    User provider = offering.getProvider();

	    List<CalendarTerm> calendarTerms = appointmentService.getFreeCalendarTerms(provider);

	    model.addAttribute("clientName", userProfileService.resolveDisplayName(currentUser));
	    model.addAttribute("offering", offering);
	    model.addAttribute("calendarTerms", calendarTerms);
	    model.addAttribute("bookableDateOptions",
	            appointmentService.getBookableDateOptions(provider, offering));

	    return "client-offering";
	}

	/**
	 * Acknowledges rejected appointment and removes it from client's list.
	 *
	 * @param appointmentId appointment identifier
	 * @param currentUser authenticated client
	 * @return redirect to client page
	 */
	@PostMapping("/client/appointments/{appointmentId}/acknowledge")
	public String acknowledgeRejectedAppointment(@PathVariable Long appointmentId,
	                                             @CurrentUser User currentUser) {
	    appointmentService.acknowledgeRejectedAppointment(currentUser, appointmentId);

	    return "redirect:/client?section=calendar";
	}

	/**
	 * Performs fake payment for appointment and confirms it.
	 *
	 * @param appointmentId appointment identifier
	 * @param currentUser authenticated client
	 * @return redirect to client page
	 */
	@PostMapping("/client/appointments/{appointmentId}/pay")
	public String payForAppointment(@PathVariable Long appointmentId,
	                                @CurrentUser User currentUser) {
	    appointmentService.payForAppointment(currentUser, appointmentId);

	    return "redirect:/client?section=calendar";
	}

	/**
	 * Cancels pending (not yet confirmed) appointment by client.
	 *
	 * @param appointmentId appointment identifier
	 * @param currentUser authenticated client
	 * @return redirect to client calendar
	 */
	@PostMapping("/client/appointments/{appointmentId}/cancel-request")
	public String cancelPendingAppointment(@PathVariable Long appointmentId,
	                                       @CurrentUser User currentUser) {
	    appointmentService.cancelPendingAppointmentByClient(currentUser, appointmentId);

	    return "redirect:/client?section=calendar";
	}

	/**
	 * Cancels confirmed appointment by client.
	 *
	 * @param appointmentId appointment identifier
	 * @param currentUser authenticated client
	 * @return redirect to client calendar
	 */
	@PostMapping("/client/appointments/{appointmentId}/cancel")
	public String cancelConfirmedAppointment(@PathVariable Long appointmentId,
	                                         @CurrentUser User currentUser) {
	    appointmentService.cancelConfirmedAppointmentByClient(currentUser, appointmentId);

	    return "redirect:/client?section=calendar";
	}
}
