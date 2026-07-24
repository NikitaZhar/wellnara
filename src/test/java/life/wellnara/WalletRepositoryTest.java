package life.wellnara;

import life.wellnara.model.Offering;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletBalance;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.ServicePackageRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.wallet.WalletLedgerCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Persistence tests for the wallet data model: the schema round-trips and the
 * persisted ledger folds to the expected balance.
 */
@DataJpaTest
class WalletRepositoryTest {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 5, 1, 10, 0);

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletEntryRepository walletEntryRepository;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    private final WalletLedgerCalculator calculator = new WalletLedgerCalculator();

    @Test
    @DisplayName("Persisted money ledger folds to the expected available and held balance")
    void persistedLedgerFolds() {
        User provider = user("prov-fold", "prov-fold@test.com", UserRole.PROVIDER);
        User client = user("cli-fold", "cli-fold@test.com", UserRole.CLIENT);
        Wallet wallet = walletRepository.save(new Wallet(client, provider, "USD", AT));

        walletEntryRepository.save(
                WalletEntry.money(wallet, WalletEntryType.TOP_UP, new BigDecimal("100.00"), null, provider, AT, "cash"));
        walletEntryRepository.save(
                WalletEntry.money(wallet, WalletEntryType.HOLD, new BigDecimal("40.00"), null, client, AT, null));
        walletEntryRepository.save(
                WalletEntry.money(wallet, WalletEntryType.SETTLE, new BigDecimal("40.00"), null, provider, AT, null));

        List<WalletEntry> ledger = walletEntryRepository.findAllByWalletOrderByIdAsc(wallet);
        WalletBalance balance = calculator.foldMoney(wallet.getCurrency(), ledger);

        assertThat(ledger).hasSize(3);
        assertThat(balance.getAvailable()).isEqualByComparingTo("60.00");
        assertThat(balance.getHeld()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("A client can have at most one wallet")
    void oneWalletPerClient() {
        User provider = user("prov-uk", "prov-uk@test.com", UserRole.PROVIDER);
        User client = user("cli-uk", "cli-uk@test.com", UserRole.CLIENT);

        walletRepository.saveAndFlush(new Wallet(client, provider, "USD", AT));

        assertThatThrownBy(() ->
                walletRepository.saveAndFlush(new Wallet(client, provider, "USD", AT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("findByClient returns the client's wallet")
    void findsWalletByClient() {
        User provider = user("prov-find", "prov-find@test.com", UserRole.PROVIDER);
        User client = user("cli-find", "cli-find@test.com", UserRole.CLIENT);
        walletRepository.save(new Wallet(client, provider, "EUR", AT));

        assertThat(walletRepository.findByClient(client)).isPresent();
        assertThat(walletRepository.existsByClient(client)).isTrue();
    }

    @Test
    @DisplayName("A service package and its session entries persist and link back")
    void persistsPackageAndSessionEntries() {
        User provider = user("prov-pkg", "prov-pkg@test.com", UserRole.PROVIDER);
        User client = user("cli-pkg", "cli-pkg@test.com", UserRole.CLIENT);
        Wallet wallet = walletRepository.save(new Wallet(client, provider, "USD", AT));
        Offering offering = offeringRepository.save(
                new Offering(provider, "10-pack", "desc", new BigDecimal("500.00"), 60));

        ServicePackage pkg = servicePackageRepository.save(
                new ServicePackage(wallet, offering, 10, new BigDecimal("500.00"), "USD", provider, AT, "intro"));
        walletEntryRepository.save(
                WalletEntry.session(wallet, WalletEntryType.PACKAGE_GRANT, 10, pkg, null, provider, AT, null));

        List<WalletEntry> entries = walletEntryRepository.findAllByServicePackageOrderByIdAsc(pkg);

        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getSessionCount()).isEqualTo(10);
        assertThat(entries.get(0).getServicePackage().getId()).isEqualTo(pkg.getId());
        assertThat(entries.get(0).getCurrency()).isEqualTo("USD");
    }

    // ===== helpers =====

    private User user(String username, String email, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("123");
        user.setRole(role);
        return userRepository.save(user);
    }
}
