package life.wellnara.config;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.Validator;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Wiring for user-facing language selection (English / Russian).
 *
 * <p>The chosen language is remembered in a cookie so it survives across sessions
 * and applies before sign-in. A visitor who has not chosen yet sees Russian, the
 * application default. The language is switched by adding {@code ?lang=en} or
 * {@code ?lang=ru} to any URL, which the {@link LocaleChangeInterceptor} reads and
 * stores in the cookie.
 *
 * <p>Bean Validation is bound to the shared {@link MessageSource} so constraint
 * messages referenced as {@code {key}} resolve from the same {@code messages}
 * bundles as the templates.
 */
@Configuration
public class LocalizationConfig implements WebMvcConfigurer {

    /** Supported UI languages; the first entry is the application default. */
    @SuppressWarnings("deprecation") // new Locale(...) — Locale.of requires Java 19+, project is on 17
    public static final List<Locale> SUPPORTED_LOCALES = List.of(new Locale("ru"), Locale.ENGLISH);

    private static final Duration LOCALE_COOKIE_MAX_AGE = Duration.ofDays(365);

    private final MessageSource messageSource;

    /**
     * Creates the localization configuration.
     *
     * @param messageSource shared application message source used for validation messages
     */
    public LocalizationConfig(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    /**
     * Resolves and persists the active locale in a cookie.
     *
     * <p>The default locale is Russian, applied when no cookie is present and the
     * request has not selected a language. The cookie is scoped to the whole
     * application and kept for a year so a returning visitor keeps their choice.
     *
     * @return cookie-backed locale resolver defaulting to Russian
     */
    @Bean
    public LocaleResolver localeResolver() {
        CookieLocaleResolver resolver = new CookieLocaleResolver("WELLNARA_LOCALE");
        resolver.setDefaultLocale(SUPPORTED_LOCALES.get(0));
        resolver.setCookiePath("/");
        resolver.setCookieMaxAge(LOCALE_COOKIE_MAX_AGE);
        return resolver;
    }

    /**
     * Reads the {@code lang} request parameter and updates the stored locale.
     *
     * <p>An unknown or malformed value is ignored rather than raising an error, so
     * a stray {@code ?lang=} never breaks a page.
     *
     * @return interceptor that applies the {@code lang} parameter
     */
    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.setIgnoreInvalidLocale(true);
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
    }

    /**
     * Binds Bean Validation to the application {@link MessageSource}.
     *
     * <p>Lets validation annotations reference message keys as {@code {key}} and
     * have them resolved from the localized {@code messages} bundles, so validation
     * errors follow the active language like the rest of the UI.
     *
     * @return validator that resolves constraint messages from the application message source
     */
    @Override
    public Validator getValidator() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messageSource);
        return validator;
    }
}
