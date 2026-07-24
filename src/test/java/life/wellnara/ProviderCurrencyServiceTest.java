package life.wellnara;

import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.OfferingService;
import life.wellnara.service.ProviderCurrencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the provider wallet currency rules: inheritance by offerings,
 * cascade on change, and the freeze once the ledger has entries.
 */
@SpringBootTest
@Transactional
class ProviderCurrencyServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 5, 1, 10, 0);

    @Autowired
    private ProviderCurrencyService providerCurrencyService;

    @Autowired
    private OfferingService offeringService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletEntryRepository walletEntryRepository;

    @Test
    @DisplayName("Offering created by a provider inherits the provider currency")
    void offeringInheritsProviderCurrency() {
        User provider = provider("prov-inherit", "EUR");

        offeringService.createOffering(provider, "Consultation", "desc", new BigDecimal("50.00"), 60);

        Offering offering = offeringRepository.findAllByProvider(provider).get(0);
        assertThat(offering.getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Changing provider currency cascades to offerings and normalises the code")
    void changingCurrencyCascadesToOfferings() {
        User provider = provider("prov-cascade", "EUR");
        offeringService.createOffering(provider, "Consultation", "desc", new BigDecimal("50.00"), 60);

        providerCurrencyService.changeCurrency(provider, "usd");

        assertThat(userRepository.findById(provider.getId()).orElseThrow().getCurrency()).isEqualTo("USD");
        assertThat(offeringRepository.findAllByProvider(provider).get(0).getCurrency()).isEqualTo("USD");
    }

    @Test
    @DisplayName("Currency change is rejected once the wallet ledger has entries")
    void currencyChangeForbiddenWithNonEmptyLedger() {
        User provider = provider("prov-frozen", "EUR");
        User client = client("client-frozen");
        Wallet wallet = walletRepository.save(new Wallet(client, provider, "EUR", NOW));
        walletEntryRepository.save(
                WalletEntry.money(wallet, WalletEntryType.TOP_UP, new BigDecimal("100.00"), null, provider, NOW, "cash"));

        assertThatThrownBy(() -> providerCurrencyService.changeCurrency(provider, "USD"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ledger");

        assertThat(userRepository.findById(provider.getId()).orElseThrow().getCurrency()).isEqualTo("EUR");
    }

    @Test
    @DisplayName("Invalid currency code is rejected")
    void invalidCurrencyRejected() {
        User provider = provider("prov-invalid", "EUR");

        assertThatThrownBy(() -> providerCurrencyService.changeCurrency(provider, "US"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported currency");
    }

    // ===== helpers =====

    private User provider(String username, String currency) {
        User user = new User();
        user.setUsername(username + "-" + System.nanoTime());
        user.setEmail(username + "-" + System.nanoTime() + "@test.com");
        user.setPassword("123");
        user.setRole(UserRole.PROVIDER);
        user.setCurrency(currency);
        return userRepository.save(user);
    }

    private User client(String username) {
        User user = new User();
        user.setUsername(username + "-" + System.nanoTime());
        user.setEmail(username + "-" + System.nanoTime() + "@test.com");
        user.setPassword("123");
        user.setRole(UserRole.CLIENT);
        return userRepository.save(user);
    }
}
