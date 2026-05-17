package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.AdminUserService;
import life.wellnara.service.SessionUserService;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Controller for admin page access and admin user management.
 */
@Controller
public class AdminController {

	private final AdminUserService adminUserService;
	private final SessionUserService sessionUserService;

	/**
	 * Creates admin controller.
	 *
	 * @param adminUserService service for admin user operations
	 */
	public AdminController(AdminUserService adminUserService,
			SessionUserService sessionUserService) {
		this.adminUserService = adminUserService;
		this.sessionUserService = sessionUserService;
	}

	/**
	 * Shows admin page for authenticated admin user.
	 *
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return admin page view name or redirect to login page
	 */
	@GetMapping("/admin")
	public String showPage(HttpSession session, Model model) {
		User currentUser = sessionUserService.requireAdmin(session);

		if (currentUser == null) {
		    return "redirect:/auth/login";
		}

		Object providerInviteLink = session.getAttribute("providerInviteLink");
		if (providerInviteLink != null) {
			model.addAttribute("providerInviteLink", providerInviteLink);
			session.removeAttribute("providerInviteLink");
		}

		model.addAttribute("users", adminUserService.getAllUsersExceptAdmins());

		return "admin";
	}

	/**
	 * Deletes non-admin user by id.
	 *
	 * @param id user identifier
	 * @param session current HTTP session
	 * @return redirect to admin page or login page
	 */
	@PostMapping("/admin/users/{id}/delete")
	public String deleteUser(@PathVariable Long id, HttpSession session) {
		User currentUser = sessionUserService.requireAdmin(session);

		if (currentUser == null) {
		    return "redirect:/auth/login";
		}

		adminUserService.deleteNonAdminUser(id);

		return "redirect:/admin";
	}
}