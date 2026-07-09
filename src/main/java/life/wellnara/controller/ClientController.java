package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.ClientOfferingService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.service.SessionUserService;
import life.wellnara.service.UserProfileService;

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
	private final SessionUserService sessionUserService;
	private final UserProfileService userProfileService;

	/**
	 * Creates client controller.
	 *
	 * @param clientOfferingService service for client access to provider offerings
	 * @param appointmentService service for appointment requests
	 * @param providerCalendarService service for provider calendar operations
	 * @param sessionUserService service for authenticated session user access
	 * @param userProfileService service for user personal data
	 */
	public ClientController(ClientOfferingService clientOfferingService,
	        AppointmentService appointmentService,
	        ProviderCalendarService providerCalendarService,
	        SessionUserService sessionUserService,
	        UserProfileService userProfileService) {
	    this.clientOfferingService = clientOfferingService;
	    this.appointmentService = appointmentService;
	    this.providerCalendarService = providerCalendarService;
	    this.sessionUserService = sessionUserService;
	    this.userProfileService = userProfileService;
	}

	/**
	 * Shows client page for authenticated client user.
	 *
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return client page view name or redirect to login page
	 */
	@GetMapping("/client")
	public String showPage(HttpSession session, Model model) {
		User currentUser = sessionUserService.requireClient(session);

		if (currentUser == null) {
			return "redirect:/auth/login";
		}

		populateClientPageModel(model, currentUser);

		return "client";
	}

	/**
	 * Creates appointment request for current client.
	 *
	 * @param providerId provider identifier
	 * @param offeringId offering identifier
	 * @param startDateTimeUtc requested start date and time in UTC
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return redirect to client page on success or client page with error
	 */
	@PostMapping("/client/appointments")
	public String requestAppointment(@RequestParam Long providerId,
	                                 @RequestParam Long offeringId,
	                                 @RequestParam LocalDate selectedDate,
	                                 @RequestParam LocalTime selectedTime,
	                                 HttpSession session,
	                                 Model model) {
		User currentUser = sessionUserService.requireClient(session);

	    if (currentUser == null) {
	        return "redirect:/auth/login";
	    }

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
	    model.addAttribute("clientName", client.getUsername());
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
	 * Updates the client profile.
	 *
	 * @param firstName new first name
	 * @param lastName  new last name
	 * @param phone     new phone number, optional
	 * @param session   current HTTP session
	 * @param model     MVC model
	 * @return redirect to the client profile tab, or the client page with an error
	 */
	@PostMapping("/client/profile")
	public String updateClientProfile(@RequestParam String firstName,
	                                  @RequestParam String lastName,
	                                  @RequestParam(required = false) String phone,
	                                  HttpSession session,
	                                  Model model) {
		User currentUser = sessionUserService.requireClient(session);

	    if (currentUser == null) {
	        return "redirect:/auth/login";
	    }

	    try {
	        userProfileService.updateProfile(currentUser, firstName, lastName, phone);
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
	
	@GetMapping("/client/offerings/{offeringId}")
	public String showOffering(@PathVariable Long offeringId,
	                           HttpSession session,
	                           Model model) {
		User currentUser = sessionUserService.requireClient(session);

	    if (currentUser == null) {
	        return "redirect:/auth/login";
	    }

	    Offering offering = clientOfferingService.getOfferingOfClientProvider(currentUser, offeringId);
	    User provider = offering.getProvider();

	    List<CalendarTerm> calendarTerms = appointmentService.getFreeCalendarTerms(provider);

	    model.addAttribute("clientName", currentUser.getUsername());
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
	 * @param session current HTTP session
	 * @return redirect to client page
	 */
	@PostMapping("/client/appointments/{appointmentId}/acknowledge")
	public String acknowledgeRejectedAppointment(@PathVariable Long appointmentId,
	                                             HttpSession session) {
		User currentUser = sessionUserService.requireClient(session);

	    if (currentUser == null) {
	        return "redirect:/auth/login";
	    }

	    appointmentService.acknowledgeRejectedAppointment(currentUser, appointmentId);

	    return "redirect:/client?section=calendar";
	}
	
	/**
	 * Performs fake payment for appointment and confirms it.
	 *
	 * @param appointmentId appointment identifier
	 * @param session current HTTP session
	 * @return redirect to client page
	 */
	@PostMapping("/client/appointments/{appointmentId}/pay")
	public String payForAppointment(@PathVariable Long appointmentId,
	                                HttpSession session) {
		User currentUser = sessionUserService.requireClient(session);

	    if (currentUser == null) {
	        return "redirect:/auth/login";
	    }

	    appointmentService.payForAppointment(currentUser, appointmentId);

	    return "redirect:/client?section=calendar";
	}
	
	@PostMapping("/client/appointments/{appointmentId}/cancel-request")
	public String cancelPendingAppointment(@PathVariable Long appointmentId,
	                                       HttpSession session) {
		User currentUser = sessionUserService.requireClient(session);

	    if (currentUser == null) {
	        return "redirect:/auth/login";
	    }

	    appointmentService.cancelPendingAppointmentByClient(currentUser, appointmentId);

	    return "redirect:/client?section=calendar";
	}	
	/**
	 * Cancels confirmed appointment by client.
	 *
	 * @param appointmentId appointment identifier
	 * @param session current HTTP session
	 * @return redirect to client calendar
	 */
	@PostMapping("/client/appointments/{appointmentId}/cancel")
	public String cancelConfirmedAppointment(@PathVariable Long appointmentId,
	                                         HttpSession session) {
		User currentUser = sessionUserService.requireClient(session);

	    if (currentUser == null) {
	        return "redirect:/auth/login";
	    }

	    appointmentService.cancelConfirmedAppointmentByClient(currentUser, appointmentId);

	    return "redirect:/client?section=calendar";
	}
}
