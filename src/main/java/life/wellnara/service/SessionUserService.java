package life.wellnara.service;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import org.springframework.stereotype.Service;

/**
 * Service for centralized access to the authenticated user stored in HTTP session.
 *
 * <p>Since step 1.2 authorization by role is enforced by the Spring Security
 * filter chain (see {@code SecurityConfig}), so this service no longer performs
 * role checks — it only exposes the current user. The whole {@link User} is
 * still kept in the session; that is removed in step 1.4, at which point this
 * service is retired in favour of the security principal.
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
     * <p>Role enforcement now lives in the security filter chain, so callers
     * only need the authenticated user, not a role-guarded accessor.
     *
     * @param session current HTTP session
     * @return authenticated user or {@code null} if none is stored
     */
    public User getCurrentUser(HttpSession session) {
        Object sessionUser = session.getAttribute(CURRENT_USER_ATTRIBUTE);

        if (!(sessionUser instanceof User currentUser)) {
            return null;
        }

        return currentUser;
    }
}
