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
 * by the time the handler runs. The user is read from the HTTP session
 * (temporary until step 1.4 moves the principal into the security context).
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
