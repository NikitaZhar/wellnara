package life.wellnara.security;

import life.wellnara.model.UserRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Lightweight, immutable authentication principal stored in the
 * {@link org.springframework.security.core.context.SecurityContext} and, through
 * it, in the HTTP session.
 *
 * <p>Replaces the previous practice of putting the whole {@link life.wellnara.model.User}
 * entity (including the password hash) into the session (step 1.4). Only the
 * data needed for authorization and for looking the user up again is kept here:
 * the persistent id, the login name and the role. The full entity is re-loaded
 * from the database per request by
 * {@link life.wellnara.web.CurrentUserArgumentResolver}, which also keeps it
 * fresh instead of serving a stale session copy.
 *
 * <p>The type is {@link Serializable} so the security context can be persisted
 * into the servlet session; it is safe to serialize because it carries no
 * secrets.
 */
public final class AuthenticatedUser implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Long id;
    private final String username;
    private final UserRole role;

    /**
     * Creates a principal from the identifying attributes of a user.
     *
     * @param id       persistent user id, must not be {@code null}
     * @param username login name, must not be {@code null}
     * @param role     user role, must not be {@code null}
     */
    public AuthenticatedUser(Long id, String username, UserRole role) {
        this.id = Objects.requireNonNull(id, "id is required");
        this.username = Objects.requireNonNull(username, "username is required");
        this.role = Objects.requireNonNull(role, "role is required");
    }

    /**
     * @return persistent id of the authenticated user
     */
    public Long getId() {
        return id;
    }

    /**
     * @return login name of the authenticated user
     */
    public String getUsername() {
        return username;
    }

    /**
     * @return role of the authenticated user
     */
    public UserRole getRole() {
        return role;
    }

    /**
     * Maps the role to the Spring Security {@code ROLE_*} authority understood
     * by the {@code hasRole(...)} rules in the security filter chain.
     *
     * @return single-element authority list for this principal's role
     */
    public List<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof AuthenticatedUser that)) {
            return false;
        }
        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "AuthenticatedUser{id=" + id + ", username=" + username + ", role=" + role + "}";
    }
}
