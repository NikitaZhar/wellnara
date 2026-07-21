package life.wellnara.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import life.wellnara.model.User;
import life.wellnara.security.SecuritySessionService;
import life.wellnara.service.ClientInvitationService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for client registration by invitation token.
 */
@Controller
public class ClientRegistrationController {

	private final ClientInvitationService clientInvitationService;
	private final SecuritySessionService securitySessionService;

	/**
	 * Creates client registration controller.
	 *
	 * @param clientInvitationService service for client invitation flow
	 * @param securitySessionService  service that establishes the security context
	 */
	public ClientRegistrationController(ClientInvitationService clientInvitationService,
			SecuritySessionService securitySessionService) {
		this.clientInvitationService = clientInvitationService;
		this.securitySessionService = securitySessionService;
	}

	/**
	 * Shows client registration page.
	 *
	 * @param token invitation token
	 * @param model MVC model
	 * @return registration page view name
	 */
	@GetMapping("/client/register")
	public String showRegisterPage(@RequestParam String token, Model model) {
		try {
			String email = clientInvitationService.getEmailByToken(token);
			model.addAttribute("token", token);
			model.addAttribute("email", email);
			return "client-register";
		} catch (IllegalArgumentException exception) {
			model.addAttribute("error", exception.getMessage());
			return "login";
		}
	}

	/**
	 * Registers client by invitation token.
	 *
	 * @param token           invitation token
	 * @param name            client name
	 * @param password        client password
	 * @param confirmPassword repeated client password
	 * @param request         current request
	 * @param response        current response
	 * @param model           MVC model
	 * @return client page on success or registration page on validation error
	 */
	@PostMapping("/client/register")
	public String register(@RequestParam String token,
			@RequestParam String name,
			@RequestParam String password,
			@RequestParam String confirmPassword,
			@RequestParam String firstName,
			@RequestParam String lastName,
			@RequestParam(required = false) String phone,
			HttpServletRequest request,
			HttpServletResponse response,
			Model model) {
		String email;

		try {
			email = clientInvitationService.getEmailByToken(token);
		} catch (IllegalArgumentException exception) {
			model.addAttribute("error", exception.getMessage());
			return "login";
		}

		if (!password.equals(confirmPassword)) {
			model.addAttribute("token", token);
			model.addAttribute("email", email);
			addProfileAttributes(model, name, firstName, lastName, phone);
			model.addAttribute("error", "Passwords do not match");
			return "client-register";
		}

		try {
			User registeredUser = clientInvitationService.register(token, name, password, firstName, lastName, phone);
			securitySessionService.establish(registeredUser, request, response);
            return "redirect:/home";
		} catch (IllegalArgumentException exception) {
			model.addAttribute("token", token);
			model.addAttribute("email", email);
			addProfileAttributes(model, name, firstName, lastName, phone);
			model.addAttribute("error", exception.getMessage());
			return "client-register";
		}
	}

	private void addProfileAttributes(Model model,
			String name,
			String firstName,
			String lastName,
			String phone) {
		model.addAttribute("name", name);
		model.addAttribute("firstName", firstName);
		model.addAttribute("lastName", lastName);
		model.addAttribute("phone", phone);
	}
}
