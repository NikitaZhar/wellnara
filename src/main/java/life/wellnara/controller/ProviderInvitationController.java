package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.AdminUserService;
import life.wellnara.service.ProviderInvitationService;
import life.wellnara.service.SessionUserService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for provider invitation from admin page.
 */
@Controller
public class ProviderInvitationController {

	private final ProviderInvitationService service;
	private final AdminUserService adminUserService;
	private final SessionUserService sessionUserService;

	/**
	 * Creates provider invitation controller.
	 *
	 * @param service provider invitation service
	 * @param adminUserService service for admin user operations
	 * @param sessionUserService service for authenticated session user access
	 */
	public ProviderInvitationController(ProviderInvitationService service,
			AdminUserService adminUserService,
			SessionUserService sessionUserService) {
		this.service = service;
		this.adminUserService = adminUserService;
		this.sessionUserService = sessionUserService;
	}

	/**
	 * Creates provider invitation and stores registration link in session.
	 *
	 * @param email invited provider email
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return redirect to admin page or admin page with validation error
	 */
	@PostMapping("/admin/invite")
	public String invite(@RequestParam String email,
			HttpSession session,
			Model model) {
		User currentUser = sessionUserService.requireAdmin(session);

		if (currentUser == null) {
		    return "redirect:/auth/login";
		}

		try {
			String token = service.invite(email);
			String registrationLink = "http://localhost:8080/provider/register?token=" + token;

			session.setAttribute("providerInviteLink", registrationLink);

			return "redirect:/admin";
		} catch (IllegalArgumentException exception) {
			session.removeAttribute("providerInviteLink");

			model.addAttribute("inviteError", exception.getMessage());
			model.addAttribute("users", adminUserService.getAllUsersExceptAdmins());
			return "admin";
		}
	}
}