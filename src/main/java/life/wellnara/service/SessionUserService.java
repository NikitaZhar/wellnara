package life.wellnara.service;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import org.springframework.stereotype.Service;

/**
 * Service for centralized access to the authenticated user stored in HTTP session.
 */
@Service
public class SessionUserService {

    private static final String CURRENT_USER_ATTRIBUTE = "currentUser";

    /**
     * Stores authenticated user in current HTTP session.
     *
     * @param session current HTTP session
     * @param user authenticated user
     */
    public void login(HttpSession session, User user) {
        session.setAttribute(CURRENT_USER_ATTRIBUTE, user);
    }

    /**
     * Invalidates current HTTP session.
     *
     * @param session current HTTP session
     */
    public void logout(HttpSession session) {
        session.removeAttribute(CURRENT_USER_ATTRIBUTE);
    }

    /**
     * Returns current authenticated user from session.
     *
     * @param session current HTTP session
     * @return authenticated user or null
     */
//    public User getCurrentUser(HttpSession session) {
//        Object sessionUser = session.getAttribute(CURRENT_USER_ATTRIBUTE);
//
//        if (!(sessionUser instanceof User currentUser)) {
//            return null;
//        }
//
//        return currentUser;
//    }

    /**
     * Returns current admin user from session.
     *
     * @param session current HTTP session
     * @return authenticated admin user or null
     */
    public User requireAdmin(HttpSession session) {
        return requireRole(session, UserRole.ADMIN);
    }

    /**
     * Returns current provider user from session.
     *
     * @param session current HTTP session
     * @return authenticated provider user or null
     */
    public User requireProvider(HttpSession session) {
        return requireRole(session, UserRole.PROVIDER);
    }

    /**
     * Returns current client user from session.
     *
     * @param session current HTTP session
     * @return authenticated client user or null
     */
    public User requireClient(HttpSession session) {
        return requireRole(session, UserRole.CLIENT);
    }

    /**
     * Returns current user only when it has required role.
     *
     * @param session current HTTP session
     * @param requiredRole required user role
     * @return authenticated user with required role or null
     */
    private User requireRole(HttpSession session, UserRole requiredRole) {
        Object sessionUser = session.getAttribute(CURRENT_USER_ATTRIBUTE);

        if (!(sessionUser instanceof User currentUser)) {
            return null;
        }

        if (currentUser.getRole() != requiredRole) {
            return null;
        }

        return currentUser;
    }
}