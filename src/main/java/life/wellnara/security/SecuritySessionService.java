package life.wellnara.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import life.wellnara.model.User;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * Establishes and clears the authenticated {@link SecurityContext} for the
 * custom (non-form) login flow, persisting it into the HTTP session.
 *
 * <p>This is the single owner of "how a user becomes authenticated" after
 * step 1.4. Controllers no longer touch the session directly: on successful
 * credential check they call {@link #establish}, on logout {@link #clear}. The
 * context is written through the same {@link SecurityContextRepository} the
 * security filter chain reads from, so a subsequent request is recognised as
 * authenticated.
 *
 * <p>Only a lightweight {@link AuthenticatedUser} principal is stored — never
 * the {@link User} entity or its password.
 */
@Service
public class SecuritySessionService {

    private final SecurityContextRepository securityContextRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();
    private final SecurityContextLogoutHandler logoutHandler = new SecurityContextLogoutHandler();

    /**
     * @param securityContextRepository repository shared with the security
     *                                  filter chain used to persist the context
     */
    public SecuritySessionService(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Authenticates the given user for the current request and persists the
     * security context so following requests stay authenticated.
     *
     * @param user     authenticated domain user
     * @param request  current request
     * @param response current response
     */
    public void establish(User user, HttpServletRequest request, HttpServletResponse response) {
        AuthenticatedUser principal =
                new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }

    /**
     * Clears the security context and invalidates the session for the current
     * request.
     *
     * @param request  current request
     * @param response current response
     */
    public void clear(HttpServletRequest request, HttpServletResponse response) {
        Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();
        logoutHandler.logout(request, response, authentication);
    }
}
