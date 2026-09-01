package life.wellnara.dto;

import java.math.BigDecimal;

/**
 * JSON payload returned to the provider after a successful wallet top-up.
 *
 * <p>Carries the refreshed money balances (so the page can update in place
 * without a reload) and the single ledger row just created (so it can be
 * inserted at the top of the payment history and briefly highlighted). All
 * figures are read back from the folded ledger after the top-up commits, so the
 * balances are authoritative rather than computed on the client.
 *
 * @param available spendable money balance after the top-up
 * @param held      money reserved against pending appointments (unchanged by a top-up)
 * @param currency  ISO 4217 currency code of the wallet
 * @param entry     the newly created top-up movement, for the history list
 */
public record TopUpResult(BigDecimal available,
                          BigDecimal held,
                          String currency,
                          Entry entry) {

    /**
     * One payment-history row, prepared for display.
     *
     * @param timestamp movement time, pre-formatted in the provider timezone
     * @param label     human-readable movement label
     * @param amount    monetary amount of the movement
     * @param currency  ISO 4217 currency code of the wallet
     */
    public record Entry(String timestamp,
                        String label,
                        BigDecimal amount,
                        String currency) {
    }
}
