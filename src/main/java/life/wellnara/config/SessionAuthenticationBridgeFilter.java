package life.wellnara.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.SessionUserService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * TEMPORARY bridge (introduced in step 1.2, removed in step 1.4).
 *
 * <p>The custom login flow still stores the authenticated {@link User} in the
 * HTTP session. Spring Security's {@code hasRole(...)} rules, however, authorize
 * against the {@link org.springframework.security.core.context.SecurityContext}.
 * This filter reads the session user via {@link SessionUserService} (the single
 * owner of the session-user contract) and publishes its role into the security
 * context as a {@code ROLE_*} authority, so the {@link SecurityConfig}
 * authorization rules can take effect.
 *
 * <p>Once step 1.4 replaces the session {@code User} with a lightweight
 * principal in the {@code SecurityContext}, this bridge becomes obsolete and is
 * deleted together with {@code SessionUserService}.
 */
public class SessionAuthenticationBridgeFilter extends OncePerRequestFilter {

    private final SessionUserService sessionUserService;

    public SessionAuthenticationBridgeFilter(SessionUserService sessionUserService) {
        this.sessionUserService = sessionUserService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        if (session != null
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            User user = sessionUserService.getCurrentUser(session);

            if (user != null && user.getRole() != null) {
                SimpleGrantedAuthority authority =
                        new SimpleGrantedAuthority("ROLE_" + user.getRole().name());
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(user, null, List.of(authority));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}
