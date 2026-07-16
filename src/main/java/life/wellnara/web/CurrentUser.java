package life.wellnara.web;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Injects the authenticated {@link life.wellnara.model.User} into a controller
 * method argument.
 *
 * <p>Authorization is enforced by the security filter chain, so a parameter
 * annotated with {@code @CurrentUser} is guaranteed to receive a non-null user
 * by the time the handler runs. Since step 1.4 the entity is loaded per request
 * from the {@code AuthenticatedUser} principal held in the security context (no
 * longer stored in the HTTP session), see
 * {@link CurrentUserArgumentResolver}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
