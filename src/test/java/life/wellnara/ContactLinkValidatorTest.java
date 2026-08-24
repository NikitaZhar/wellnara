package life.wellnara;

import life.wellnara.service.ContactLinkValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link ContactLinkValidator}: allowed hosts pass, blank means "no
 * link", and anything else (wrong scheme, foreign host, garbage) is rejected.
 */
class ContactLinkValidatorTest {

    private final ContactLinkValidator validator = new ContactLinkValidator();

    @Test
    @DisplayName("A valid WhatsApp link is accepted and returned trimmed")
    void acceptsValidWhatsappLink() {
        assertThat(validator.normalizeWhatsapp("  https://wa.me/1234567890  ")).isEqualTo("https://wa.me/1234567890");
        assertThat(validator.normalizeWhatsapp("https://api.whatsapp.com/send?phone=1234567890"))
                .isEqualTo("https://api.whatsapp.com/send?phone=1234567890");
        assertThat(validator.normalizeWhatsapp("https://www.wa.me/1234567890")).isEqualTo("https://www.wa.me/1234567890");
    }

    @Test
    @DisplayName("A valid Telegram link is accepted")
    void acceptsValidTelegramLink() {
        assertThat(validator.normalizeTelegram("https://t.me/yourname")).isEqualTo("https://t.me/yourname");
    }

    @Test
    @DisplayName("A blank or null link normalizes to null (no link / removed)")
    void blankMeansNoLink() {
        assertThat(validator.normalizeWhatsapp(null)).isNull();
        assertThat(validator.normalizeWhatsapp("   ")).isNull();
        assertThat(validator.normalizeTelegram("")).isNull();
    }

    @Test
    @DisplayName("A link with a non-http(s) scheme is rejected")
    void rejectsNonHttpScheme() {
        assertThatThrownBy(() -> validator.normalizeWhatsapp("ftp://wa.me/123"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.normalizeTelegram("javascript:alert(1)"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("A link to a foreign host is rejected")
    void rejectsForeignHost() {
        assertThatThrownBy(() -> validator.normalizeWhatsapp("https://evil.example.com/1234567890"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> validator.normalizeTelegram("https://wa.me/1234567890"))
                .isInstanceOf(IllegalArgumentException.class); // WhatsApp host is not valid for Telegram
    }

    @Test
    @DisplayName("A malformed URL is rejected")
    void rejectsMalformedUrl() {
        assertThatThrownBy(() -> validator.normalizeWhatsapp("not a url"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
