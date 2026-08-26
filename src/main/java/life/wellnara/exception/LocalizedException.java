package life.wellnara.exception;

import org.springframework.context.MessageSource;

import java.util.Locale;

/**
 * A user-facing error that carries a message code for localization.
 *
 * <p>Extends {@link IllegalArgumentException} so existing {@code catch
 * (IllegalArgumentException)} handlers keep working unchanged. It also stores an
 * English default message, returned by {@link #getMessage()}, so service-level
 * tests that assert on the exception text stay valid; the localized text is
 * produced only where a request locale is available, via {@link #resolve}.
 */
public class LocalizedException extends IllegalArgumentException {

    private final String messageKey;
    private final transient Object[] args;

    /**
     * Creates a localizable exception.
     *
     * @param messageKey     message code resolved against the application {@code messages} bundles
     * @param defaultMessage English fallback text, also returned by {@link #getMessage()}
     * @param args           optional message arguments for {@code {0}}, {@code {1}}… placeholders
     */
    public LocalizedException(String messageKey, String defaultMessage, Object... args) {
        super(defaultMessage);
        this.messageKey = messageKey;
        this.args = args != null ? args.clone() : new Object[0];
    }

    /**
     * @return the message code for this error
     */
    public String getMessageKey() {
        return messageKey;
    }

    /**
     * Resolves this error's text in the given locale, falling back to the English
     * default message if the code is not present in the bundle.
     *
     * @param messageSource application message source
     * @param locale        target locale
     * @return localized (or fallback) message text
     */
    public String resolve(MessageSource messageSource, Locale locale) {
        return messageSource.getMessage(messageKey, args, getMessage(), locale);
    }

    /**
     * Resolves a caught argument exception to display text: localized when it is a
     * {@link LocalizedException}, otherwise its plain message.
     *
     * @param exception     caught exception
     * @param messageSource application message source
     * @param locale        target locale
     * @return text to show to the user
     */
    public static String resolve(IllegalArgumentException exception,
                                 MessageSource messageSource,
                                 Locale locale) {
        if (exception instanceof LocalizedException localized) {
            return localized.resolve(messageSource, locale);
        }
        return exception.getMessage();
    }
}
