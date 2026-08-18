package life.wellnara.service.calendar;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Generates opaque, URL-safe tokens for personal calendar feeds.
 *
 * <p>Each token is 32 bytes of cryptographically strong randomness, encoded as
 * URL-safe Base64 without padding, so it is safe to place directly in a feed
 * path and infeasible to guess.
 */
@Component
public class CalendarFeedTokenGenerator {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();

    /**
     * Generates a new feed token.
     *
     * @return URL-safe, unpadded Base64 token
     */
    public String generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);

        return encoder.encodeToString(bytes);
    }
}
