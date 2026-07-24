package life.wellnara.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Immutable money balance of a wallet: a fold over the money ledger.
 *
 * <p>{@code available} is spendable; {@code held} is reserved against pending
 * appointments. Both are guaranteed non-negative by the ledger invariants
 * (enforced when entries are created, from step 3.5 on).
 */
public final class WalletBalance {

    private final BigDecimal available;
    private final BigDecimal held;
    private final String currency;

    /**
     * Creates a money balance.
     *
     * @param available spendable amount
     * @param held amount reserved against pending appointments
     * @param currency ISO 4217 currency code
     */
    public WalletBalance(BigDecimal available, BigDecimal held, String currency) {
        this.available = available;
        this.held = held;
        this.currency = currency;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getHeld() {
        return held;
    }

    public String getCurrency() {
        return currency;
    }

    /**
     * Returns the total held in the wallet.
     *
     * @return {@code available + held}
     */
    public BigDecimal getTotal() {
        return available.add(held);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof WalletBalance that)) {
            return false;
        }
        return available.compareTo(that.available) == 0
                && held.compareTo(that.held) == 0
                && Objects.equals(currency, that.currency);
    }

    @Override
    public int hashCode() {
        return Objects.hash(available.stripTrailingZeros(), held.stripTrailingZeros(), currency);
    }

    @Override
    public String toString() {
        return "WalletBalance{available=" + available + ", held=" + held + ", currency=" + currency + '}';
    }
}
