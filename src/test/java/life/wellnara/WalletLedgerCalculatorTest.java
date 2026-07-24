package life.wellnara;

import life.wellnara.model.SessionBalance;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletBalance;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.service.wallet.WalletLedgerCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pure unit tests for folding the wallet ledger into balances.
 *
 * <p>No Spring context and no database: the calculator is a pure function of its
 * entries, so entities are built in memory.
 */
class WalletLedgerCalculatorTest {

    private static final String USD = "USD";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 5, 1, 10, 0);

    private final WalletLedgerCalculator calculator = new WalletLedgerCalculator();

    @Test
    @DisplayName("Empty ledger folds to zero available and held")
    void emptyLedgerIsZero() {
        WalletBalance balance = calculator.foldMoney(USD, List.of());

        assertThat(balance.getAvailable()).isEqualByComparingTo("0.00");
        assertThat(balance.getHeld()).isEqualByComparingTo("0.00");
        assertThat(balance.getTotal()).isEqualByComparingTo("0.00");
        assertThat(balance.getCurrency()).isEqualTo(USD);
    }

    @Test
    @DisplayName("TOP_UP, HOLD, RELEASE, SETTLE and ADJUSTMENT fold to the expected money balance")
    void moneyLedgerFolds() {
        Wallet wallet = wallet();
        User actor = user();

        List<WalletEntry> entries = List.of(
                money(wallet, WalletEntryType.TOP_UP, "100.00", actor),
                money(wallet, WalletEntryType.HOLD, "30.00", actor),
                money(wallet, WalletEntryType.RELEASE, "10.00", actor),
                money(wallet, WalletEntryType.SETTLE, "20.00", actor),
                money(wallet, WalletEntryType.ADJUSTMENT, "-5.00", actor)
        );

        WalletBalance balance = calculator.foldMoney(USD, entries);

        // available: 100 - 30 + 10 - 5 = 75 ; held: +30 - 10 - 20 = 0
        assertThat(balance.getAvailable()).isEqualByComparingTo("75.00");
        assertThat(balance.getHeld()).isEqualByComparingTo("0.00");
        assertThat(balance.getTotal()).isEqualByComparingTo("75.00");
    }

    @Test
    @DisplayName("A pending HOLD leaves funds in held, not available")
    void holdMovesAvailableToHeld() {
        Wallet wallet = wallet();
        User actor = user();

        List<WalletEntry> entries = List.of(
                money(wallet, WalletEntryType.TOP_UP, "50.00", actor),
                money(wallet, WalletEntryType.HOLD, "20.00", actor)
        );

        WalletBalance balance = calculator.foldMoney(USD, entries);

        assertThat(balance.getAvailable()).isEqualByComparingTo("30.00");
        assertThat(balance.getHeld()).isEqualByComparingTo("20.00");
    }

    @Test
    @DisplayName("Session entries are ignored by the money fold")
    void sessionEntriesIgnoredByMoneyFold() {
        Wallet wallet = wallet();
        User actor = user();

        List<WalletEntry> entries = List.of(
                money(wallet, WalletEntryType.TOP_UP, "40.00", actor),
                session(wallet, WalletEntryType.PACKAGE_GRANT, 5, actor)
        );

        WalletBalance balance = calculator.foldMoney(USD, entries);

        assertThat(balance.getAvailable()).isEqualByComparingTo("40.00");
        assertThat(balance.getHeld()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Package session entries fold to remaining available and held sessions")
    void sessionLedgerFolds() {
        Wallet wallet = wallet();
        User actor = user();

        List<WalletEntry> entries = List.of(
                session(wallet, WalletEntryType.PACKAGE_GRANT, 10, actor),
                session(wallet, WalletEntryType.PACKAGE_HOLD, 3, actor),
                session(wallet, WalletEntryType.PACKAGE_RELEASE, 1, actor),
                session(wallet, WalletEntryType.PACKAGE_CONSUME, 1, actor),
                money(wallet, WalletEntryType.TOP_UP, "99.00", actor)
        );

        SessionBalance balance = calculator.foldSessions(entries);

        // available: 10 - 3 + 1 = 8 ; held: +3 - 1 - 1 = 1
        assertThat(balance.getAvailable()).isEqualTo(8);
        assertThat(balance.getHeld()).isEqualTo(1);
        assertThat(balance.getTotal()).isEqualTo(9);
    }

    // ===== helpers =====

    private Wallet wallet() {
        return new Wallet(user(), user(), USD, AT);
    }

    private User user() {
        return new User();
    }

    private WalletEntry money(Wallet wallet, WalletEntryType type, String amount, User actor) {
        return WalletEntry.money(wallet, type, new BigDecimal(amount), null, actor, AT, null);
    }

    private WalletEntry session(Wallet wallet, WalletEntryType type, int count, User actor) {
        ServicePackage none = null;
        return WalletEntry.session(wallet, type, count, none, null, actor, AT, null);
    }
}
