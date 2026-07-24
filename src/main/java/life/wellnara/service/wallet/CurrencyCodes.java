package life.wellnara.service.wallet;

import java.util.Currency;
import java.util.List;
import java.util.Locale;

/**
 * ISO 4217 currency-code helpers shared across the wallet domain.
 *
 * <p>{@link #SUPPORTED} is a UI convenience list only. The source of truth is
 * {@link #normalize(String)}, which accepts any code Java recognises as ISO 4217,
 * so adding a currency to the picker never requires touching validation.
 */
public final class CurrencyCodes {

    /** Currencies offered in the UI pickers (registration, provider profile). */
    public static final List<String> SUPPORTED = List.of("EUR", "USD", "GBP", "CZK", "PLN", "CHF");

    /** Default pre-selected currency. */
    public static final String DEFAULT = "EUR";

    private CurrencyCodes() {
    }

    /**
     * Validates and canonicalises an ISO 4217 currency code.
     *
     * @param code raw currency code (any case, may have surrounding spaces)
     * @return the upper-cased, validated code
     * @throws IllegalArgumentException if the code is missing or not a valid ISO 4217 code
     */
    public static String normalize(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Currency is required");
        }

        String normalized = code.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException notIso4217) {
            throw new IllegalArgumentException("Unsupported currency: " + code);
        }

        return normalized;
    }
}
