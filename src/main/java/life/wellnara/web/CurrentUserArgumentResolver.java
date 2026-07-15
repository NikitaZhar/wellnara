package life.wellnara.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.SessionUserService;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Resolves {@link CurrentUser}-annotated controller arguments to the
 * authenticated {@link User} stored in the HTTP session.
 *
 * <p>Removes the repeated "load current user and redirect if null" boilerplate
 * from controllers: unauthenticated requests are already redirected by the
 * security filter chain, so by the time a handler runs the user is present.
 */
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    private final SessionUserService sessionUserService;

    public CurrentUserArgumentResolver(SessionUserService sessionUserService) {
        this.sessionUserService = sessionUserService;
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
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        HttpSession session = request != null ? request.getSession(false) : null;
        User currentUser = session != null ? sessionUserService.getCurrentUser(session) : null;

        if (currentUser == null) {
            throw new IllegalStateException(
                    "No authenticated user in session for a @CurrentUser parameter; "
                            + "the security filter chain must guarantee authentication here");
        }

        return currentUser;
    }
}
