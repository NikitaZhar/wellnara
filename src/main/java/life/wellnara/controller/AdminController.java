package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.service.AdminUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Controller for admin page access and admin user management.
 *
 * <p>Access is restricted to the {@code ADMIN} role by the security filter
 * chain, so the handlers no longer perform role checks themselves.
 */
@Controller
public class AdminController {

    private final AdminUserService adminUserService;

    /**
     * Creates admin controller.
     *
     * @param adminUserService service for admin user operations
     */
    public AdminController(AdminUserService adminUserService) {
        this.adminUserService = adminUserService;
    }

    /**
     * Shows admin page for authenticated admin user.
     *
     * @param session current HTTP session
     * @param model MVC model
     * @return admin page view name
     */
    @GetMapping("/admin")
    public String showPage(HttpSession session, Model model) {
        Object providerInviteSuccessMessage = session.getAttribute("providerInviteSuccessMessage");
        if (providerInviteSuccessMessage != null) {
            model.addAttribute("providerInviteSuccessMessage", providerInviteSuccessMessage);
            session.removeAttribute("providerInviteSuccessMessage");
        }

        model.addAttribute("users", adminUserService.getAllUsersExceptAdmins());

        return "admin";
    }

    /**
     * Deletes non-admin user by id.
     *
     * @param id user identifier
     * @return redirect to admin page
     */
    @PostMapping("/admin/users/{id}/delete")
    public String deleteUser(@PathVariable Long id) {
        adminUserService.deleteNonAdminUser(id);

        return "redirect:/admin";
    }
}
