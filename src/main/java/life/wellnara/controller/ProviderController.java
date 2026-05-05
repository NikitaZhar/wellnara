package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.exception.CalendarValidationException;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.OfferingService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.service.ProviderClientService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for provider page access and provider calendar form submission.
 */
@Controller
public class ProviderController {

	private final ProviderClientService providerClientService;
	private final OfferingService offeringService;
	private final ProviderCalendarService providerCalendarService;
	private final AppointmentService appointmentService;

	/**
	 * Creates provider controller.
	 *
	 * @param providerClientService service for provider client operations
	 * @param offeringService service for offering management
	 * @param providerCalendarService service for provider calendar management
	 */
	public ProviderController(ProviderClientService providerClientService,
			OfferingService offeringService,
			ProviderCalendarService providerCalendarService,
			AppointmentService appointmentService) {
		this.providerClientService = providerClientService;
		this.offeringService = offeringService;
		this.providerCalendarService = providerCalendarService;
		this.appointmentService = appointmentService;
	}

	/**
	 * Shows provider page for authenticated provider user.
	 *
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return provider page view name or redirect to login page
	 */
	@GetMapping("/provider")
	public String showPage(HttpSession session, Model model) {
		User currentUser = getAuthenticatedProvider(session);

		if (currentUser == null) {
			return "redirect:/auth/login";
		}

		providerCalendarService.deleteExpiredAvailabilityPeriods(currentUser);
		appointmentService.deleteExpiredUnpaidAppointments();

		Object clientInviteLink = session.getAttribute("clientInviteLink");
		if (clientInviteLink != null) {
			model.addAttribute("clientInviteLink", clientInviteLink);
			session.removeAttribute("clientInviteLink");
		}

		Object clientInviteError = session.getAttribute("clientInviteError");
		if (clientInviteError != null) {
			model.addAttribute("clientInviteError", clientInviteError);
			session.removeAttribute("clientInviteError");
		}

		populateProviderPageModel(model, currentUser);

		return "provider";
	}

	/**
	 * Saves provider calendar availability settings.
	 *
	 * @param form provider calendar form
	 * @param session current HTTP session
	 * @return redirect to provider calendar section or login page
	 */
	@PostMapping("/provider/calendar")
	public String saveCalendar(@ModelAttribute ProviderCalendarForm form,
			HttpSession session,
			Model model) {

		User currentUser = getAuthenticatedProvider(session);

		if (currentUser == null) {
			return "redirect:/auth/login";
		}

		try {
			providerCalendarService.saveCalendar(currentUser, form);

			return "redirect:/provider?section=calendar";

		} catch (CalendarValidationException exception) {
			populateProviderPageModel(model, currentUser);
			model.addAttribute("calendarErrors", exception.getFieldErrors());

			return "provider";
		}
	}

	/**
	 * Returns authenticated provider from current session.
	 *
	 * @param session current HTTP session
	 * @return authenticated provider or null
	 */
	private User getAuthenticatedProvider(HttpSession session) {
		Object sessionUser = session.getAttribute("currentUser");

		if (!(sessionUser instanceof User currentUser)) {
			return null;
		}

		if (currentUser.getRole() != UserRole.PROVIDER) {
			return null;
		}

		return currentUser;
	}

	/**
	 * Adds common provider page data to model.
	 *
	 * @param model MVC model
	 * @param provider authenticated provider
	 */
	private void populateProviderPageModel(Model model, User provider) {
	    model.addAttribute("clients", providerClientService.getClientsOfProvider(provider));
	    model.addAttribute("offerings", offeringService.getOfferingsOfProvider(provider));
	    model.addAttribute("providerName", provider.getUsername());
	    model.addAttribute("appointments", appointmentService.getAppointmentViewsOfProvider(provider));
	    model.addAttribute("confirmedAppointments",
	            appointmentService.getConfirmedAppointmentViewsOfProvider(provider));

	    populateCalendarModel(model, provider);
	}

	/**
	 * Adds provider calendar data to model.
	 *
	 * @param model MVC model
	 * @param provider authenticated provider
	 */
	private void populateCalendarModel(Model model, User provider) {
		ProviderCalendarForm calendarForm = providerCalendarService.getLatestCalendarForm(provider);

		model.addAttribute("calendarForm", calendarForm);
		model.addAttribute("planningFrom", calendarForm.getPlanningFrom());
		model.addAttribute("planningTo", calendarForm.getPlanningTo());
		model.addAttribute("calendarTerms", appointmentService.getFreeCalendarTerms(provider));
	}

	/**
	 * Rejects client appointment request.
	 *
	 * @param appointmentId appointment identifier
	 * @param rejectionReason reason shown to client
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return redirect to provider page or provider page with validation error
	 */
	@PostMapping("/provider/appointments/{appointmentId}/reject")
	public String rejectAppointment(@PathVariable Long appointmentId,
			@RequestParam String rejectionReason,
			HttpSession session,
			Model model) {
		User currentUser = getAuthenticatedProvider(session);

		if (currentUser == null) {
			return "redirect:/auth/login";
		}

		try {
			appointmentService.rejectAppointment(
					currentUser,
					appointmentId,
					rejectionReason
					);

			return "redirect:/provider";

		} catch (IllegalArgumentException exception) {
			model.addAttribute("appointmentActionError", exception.getMessage());
			populateProviderPageModel(model, currentUser);

			return "provider";
		}
	}

	/**
	 * Accepts appointment request and asks client for payment.
	 *
	 * @param appointmentId appointment identifier
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return redirect to provider page or provider page with validation error
	 */
	@PostMapping("/provider/appointments/{appointmentId}/request-payment")
	public String requestPaymentForAppointment(@PathVariable Long appointmentId,
			HttpSession session,
			Model model) {
		User currentUser = getAuthenticatedProvider(session);

		if (currentUser == null) {
			return "redirect:/auth/login";
		}

		try {
			appointmentService.requestPaymentForAppointment(currentUser, appointmentId);

			return "redirect:/provider";

		} catch (IllegalArgumentException exception) {
			model.addAttribute("appointmentActionError", exception.getMessage());
			populateProviderPageModel(model, currentUser);

			return "provider";
		}
	}
	
	/**
	 * Cancels confirmed appointment and removes it from provider calendar.
	 *
	 * @param appointmentId appointment identifier
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return redirect to provider calendar section or provider page with error
	 */
	@PostMapping("/provider/appointments/{appointmentId}/cancel")
	public String cancelConfirmedAppointment(@PathVariable Long appointmentId,
	                                         HttpSession session,
	                                         Model model) {
	    User currentUser = getAuthenticatedProvider(session);

	    if (currentUser == null) {
	        return "redirect:/auth/login";
	    }

	    try {
	        appointmentService.cancelConfirmedAppointment(currentUser, appointmentId);

	        return "redirect:/provider?section=provider-calendar";

	    } catch (IllegalArgumentException exception) {
	        model.addAttribute("appointmentActionError", exception.getMessage());
	        populateProviderPageModel(model, currentUser);

	        return "provider";
	    }
	}
	
	/**
	 * Completes confirmed appointment.
	 *
	 * @param appointmentId appointment identifier
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return redirect to provider calendar section or provider page with error
	 */
	@PostMapping("/provider/appointments/{appointmentId}/complete")
	public String completeConfirmedAppointment(@PathVariable Long appointmentId,
	                                           HttpSession session,
	                                           Model model) {
	    User currentUser = getAuthenticatedProvider(session);

	    if (currentUser == null) {
	        return "redirect:/auth/login";
	    }

	    try {
	        appointmentService.completeConfirmedAppointment(currentUser, appointmentId);

	        return "redirect:/provider?section=provider-calendar";

	    } catch (IllegalArgumentException exception) {
	        model.addAttribute("appointmentActionError", exception.getMessage());
	        populateProviderPageModel(model, currentUser);

	        return "provider";
	    }
	}
}