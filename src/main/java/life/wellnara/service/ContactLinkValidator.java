package life.wellnara.service;

import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;

/**
 * Validates and normalizes optional provider contact links (WhatsApp, Telegram).
 *
 * <p>A blank value means "no link" (used to remove one). A non-blank value must be
 * an {@code http(s)} URL whose host belongs to the platform, to keep the links
 * trustworthy and avoid arbitrary/phishing URLs.
 */
@Component
public class ContactLinkValidator {

    private static final Set<String> WHATSAPP_HOSTS =
            Set.of("wa.me", "api.whatsapp.com", "whatsapp.com", "chat.whatsapp.com");
    private static final Set<String> TELEGRAM_HOSTS =
            Set.of("t.me", "telegram.me", "telegram.dog");

    /**
     * Validates a WhatsApp link.
     *
     * @param url raw input (may be null or blank)
     * @return the trimmed link, or {@code null} when blank
     * @throws IllegalArgumentException if the link is not a valid WhatsApp URL
     */
    public String normalizeWhatsapp(String url) {
        return normalize(url, WHATSAPP_HOSTS, "WhatsApp", "wa.me");
    }

    /**
     * Validates a Telegram link.
     *
     * @param url raw input (may be null or blank)
     * @return the trimmed link, or {@code null} when blank
     * @throws IllegalArgumentException if the link is not a valid Telegram URL
     */
    public String normalizeTelegram(String url) {
        return normalize(url, TELEGRAM_HOSTS, "Telegram", "t.me");
    }

    private String normalize(String rawUrl, Set<String> allowedHosts, String label, String example) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return null; // blank = no link / remove
        }
        String trimmed = rawUrl.trim();

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException(label + " link is not a valid URL");
        }

        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException(label + " link must start with http:// or https://");
        }

        String host = uri.getHost();
        if (host == null) {
            throw new IllegalArgumentException(label + " link is not a valid URL");
        }
        String normalizedHost = host.toLowerCase(Locale.ROOT);
        if (normalizedHost.startsWith("www.")) {
            normalizedHost = normalizedHost.substring(4);
        }
        if (!allowedHosts.contains(normalizedHost)) {
            throw new IllegalArgumentException(label + " link must point to " + example);
        }

        return trimmed;
    }
}
