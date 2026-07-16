package life.wellnara;

import life.wellnara.model.User;
import life.wellnara.security.AuthenticatedUser;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

/**
 * Test support for authenticating MVC requests as a persisted {@link User}.
 *
 * <p>Since step 1.4 authentication is carried by an {@link AuthenticatedUser}
 * principal held in the security context and persisted into the HTTP session by
 * {@link HttpSessionSecurityContextRepository}. This helper builds a session
 * that already contains that context — exactly the state the real login flow
 * leaves behind — so tests authenticate by passing it via {@code .session(...)},
 * matching production instead of relying on test-only holder plumbing.
 */
final class SecurityTestSupport {

    private SecurityTestSupport() {
    }

    /**
     * Builds an HTTP session pre-populated with the security context for the
     * given user, ready to be attached to a request via {@code .session(...)}.
     *
     * @param user persisted user (must already have an id and role)
     * @return session carrying the matching {@link AuthenticatedUser} principal
     */
    static MockHttpSession authenticatedSession(User user) {
        AuthenticatedUser principal =
                new AuthenticatedUser(user.getId(), user.getUsername(), user.getRole());
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                principal, null, principal.getAuthorities());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);

        MockHttpSession session = new MockHttpSession();
        session.setAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        return session;
    }
}
