package life.wellnara.web;

import life.wellnara.model.User;
import life.wellnara.repository.UserRepository;
import life.wellnara.security.AuthenticatedUser;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUser}-annotated controller arguments to a freshly
 * loaded {@link User} entity.
 *
 * <p>Since step 1.4 the session no longer holds the {@code User} entity — only a
 * lightweight {@link AuthenticatedUser} principal lives in the security context.
 * This resolver reads that principal and re-loads the entity by id, so:
 * <ul>
 *   <li>controllers and services keep working with a real, managed {@code User};</li>
 *   <li>the entity is always current instead of a stale session snapshot.</li>
 * </ul>
 *
 * <p>Authorization is enforced upstream by the security filter chain, so a
 * handler is only reached when authenticated; a missing principal or a
 * vanished user therefore signals a server-side inconsistency and fails fast.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final UserRepository userRepository;
    private final SecurityContextHolderStrategy securityContextHolderStrategy =
            SecurityContextHolder.getContextHolderStrategy();

    /**
     * @param userRepository repository used to load the authenticated user
     */
    public CurrentUserArgumentResolver(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
                && User.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        AuthenticatedUser principal = currentPrincipal();

        return userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalStateException(
                        "Authenticated principal " + principal.getId()
                                + " has no matching user; security context and database are out of sync"));
    }

    private AuthenticatedUser currentPrincipal() {
        Authentication authentication = securityContextHolderStrategy.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser principal)) {
            throw new IllegalStateException(
                    "No authenticated principal for a @CurrentUser parameter; "
                            + "the security filter chain must guarantee authentication here");
        }

        return principal;
    }
}
