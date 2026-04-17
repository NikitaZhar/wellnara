package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.service.AdminUserService;
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
     * @return admin page view name or redirect to login page
     */
    @GetMapping("/admin")
    public String showPage(HttpSession session, Model model) {
        Object sessionUser = session.getAttribute("currentUser");

        if (!(sessionUser instanceof User currentUser)) {
            return "redirect:/auth/login";
        }

        if (currentUser.getRole() != UserRole.ADMIN) {
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
        Object sessionUser = session.getAttribute("currentUser");

        if (!(sessionUser instanceof User currentUser)) {
            return "redirect:/auth/login";
        }

        if (currentUser.getRole() != UserRole.ADMIN) {
            return "redirect:/auth/login";
        }

        adminUserService.deleteNonAdminUser(id);

        return "redirect:/admin";
    }
}