package life.wellnara.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Repository that stores the {@link org.springframework.security.core.context.SecurityContext}
     * in the HTTP session.
     *
     * <p>Exposed as a bean so the same instance is used both by the security
     * filter chain (to read the context on each request) and by
     * {@link life.wellnara.security.SecuritySessionService} (to save it at
     * login). Sharing one repository keeps save and load symmetric.
     */
    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /**
     * Authorization by roles for the whole application.
     *
     * <p>Public endpoints (login, logout, self-registration, static assets) are
     * open; the {@code /admin}, {@code /provider} and {@code /client} areas are
     * restricted to the matching role; everything else requires authentication.
     *
     * <p>Since step 1.4 authentication is carried by a lightweight
     * {@code AuthenticatedUser} principal that the custom login flow writes into
     * {@code securityContextRepository}; the former session-to-context bridge
     * filter is gone. Unauthenticated access is redirected to the custom login
     * page to preserve the existing UX and MVC-test expectations.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SecurityContextRepository securityContextRepository) throws Exception {
        http
            // CSRF включён (по умолчанию). Токен в POST-формы Thymeleaf
            // Spring Security подставляет автоматически через th:action.
            .csrf(withDefaults())
            .securityContext(context -> context
                .securityContextRepository(securityContextRepository)
                .requireExplicitSave(true))
            .headers(headers -> headers
                .contentTypeOptions(withDefaults())                 // X-Content-Type-Options: nosniff
                .frameOptions(frameOptions -> frameOptions.deny())  // X-Frame-Options: DENY
                .httpStrictTransportSecurity(hsts -> hsts           // HSTS (отдаётся по HTTPS)
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31_536_000)))
            .authorizeHttpRequests(auth -> auth
                // Публичные маршруты — до правил по ролям, т.к. регистрация
                // живёт под /client и /provider.
                .requestMatchers("/auth/login", "/auth/logout").permitAll()
                .requestMatchers("/client/register", "/provider/register").permitAll()
                .requestMatchers("/css/**", "/favicon.ico", "/error").permitAll()
                // Разделы по ролям.
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .requestMatchers("/provider/**").hasRole("PROVIDER")
                .requestMatchers("/client/**").hasRole("CLIENT")
                .anyRequest().authenticated())
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint((request, response, authException) ->
                    response.sendRedirect(request.getContextPath() + "/auth/login")))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .logout(logout -> logout.disable());

        return http.build();
    }
}
