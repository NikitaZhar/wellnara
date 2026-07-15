package life.wellnara.config;

import life.wellnara.service.SessionUserService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Authorization by roles for the whole application.
     *
     * <p>Public endpoints (login, logout, self-registration, static assets) are
     * open; the {@code /admin}, {@code /provider} and {@code /client} areas are
     * restricted to the matching role; everything else requires authentication.
     *
     * <p>Roles are read from the HTTP session by
     * {@link SessionAuthenticationBridgeFilter} (temporary bridge, removed in
     * step 1.4). Unauthenticated access is redirected to the custom login page
     * to preserve the existing UX and MVC-test expectations.
     *
     * <p>CSRF stays disabled here and is enabled in step 1.3.
     */
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           SessionUserService sessionUserService) throws Exception {
        http
            // CSRF включён (по умолчанию). Токен в POST-формы Thymeleaf
            // Spring Security подставляет автоматически через th:action.
            .csrf(withDefaults())
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
            .logout(logout -> logout.disable())
            .addFilterBefore(new SessionAuthenticationBridgeFilter(sessionUserService),
                    UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
