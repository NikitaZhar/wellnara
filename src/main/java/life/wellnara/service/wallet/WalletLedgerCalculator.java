package life.wellnara.service.wallet;

import life.wellnara.model.SessionBalance;
import life.wellnara.model.WalletBalance;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Folds an append-only wallet ledger into its balances.
 *
 * <p>The calculator is stateless and side-effect free: it never touches the
 * database and never mutates its inputs, so balances are always a pure function
 * of the entries handed to it. Money and session ledgers are folded
 * independently — money entries are ignored by the session fold and vice versa.
 */
@Component
public class WalletLedgerCalculator {

    private static final int MONEY_SCALE = 2;

    /**
     * Folds money-ledger entries into an available / held balance.
     *
     * <p>Rules (see the money model in the plan): {@code TOP_UP} raises available;
     * {@code HOLD} moves available to held; {@code RELEASE} moves held back to
     * available; {@code SETTLE} removes from held; {@code ADJUSTMENT} changes
     * available (its amount may be negative). Session entries are skipped.
     *
     * @param currency ISO 4217 currency code stamped on the resulting balance
     * @param entries ledger entries in any order
     * @return the folded money balance
     */
    public WalletBalance foldMoney(String currency, Iterable<WalletEntry> entries) {
        BigDecimal available = BigDecimal.ZERO;
        BigDecimal held = BigDecimal.ZERO;

        for (WalletEntry entry : entries) {
            if (!entry.getType().isMoney()) {
                continue;
            }
            BigDecimal amount = entry.getAmount();
            switch (entry.getType()) {
                case TOP_UP, ADJUSTMENT -> available = available.add(amount);
                case HOLD -> {
                    available = available.subtract(amount);
                    held = held.add(amount);
                }
                case RELEASE -> {
                    available = available.add(amount);
                    held = held.subtract(amount);
                }
                case SETTLE -> held = held.subtract(amount);
                default -> throw unexpected(entry.getType());
            }
        }

        return new WalletBalance(
                available.setScale(MONEY_SCALE, java.math.RoundingMode.UNNECESSARY),
                held.setScale(MONEY_SCALE, java.math.RoundingMode.UNNECESSARY),
                currency
        );
    }

    /**
     * Folds session-ledger entries into an available / held session balance.
     *
     * <p>Rules: {@code PACKAGE_GRANT} raises available; {@code PACKAGE_HOLD} moves
     * available to held; {@code PACKAGE_RELEASE} moves held back to available;
     * {@code PACKAGE_CONSUME} removes from held. Money entries are skipped. Pass
     * the entries of a single {@code ServicePackage} to get that package's
     * remaining sessions.
     *
     * @param entries ledger entries in any order
     * @return the folded session balance
     */
    public SessionBalance foldSessions(Iterable<WalletEntry> entries) {
        int available = 0;
        int held = 0;

        for (WalletEntry entry : entries) {
            if (!entry.getType().isSession()) {
                continue;
            }
            int count = entry.getSessionCount();
            switch (entry.getType()) {
                case PACKAGE_GRANT -> available += count;
                case PACKAGE_HOLD -> {
                    available -= count;
                    held += count;
                }
                case PACKAGE_RELEASE -> {
                    available += count;
                    held -= count;
                }
                case PACKAGE_CONSUME -> held -= count;
                default -> throw unexpected(entry.getType());
            }
        }

        return new SessionBalance(available, held);
    }

    private IllegalStateException unexpected(WalletEntryType type) {
        return new IllegalStateException("Unhandled wallet entry type: " + type);
    }
}
