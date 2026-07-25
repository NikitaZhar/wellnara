package life.wellnara;

import life.wellnara.dto.ClientWalletView;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.WalletCommandService;
import life.wellnara.service.WalletQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the read side of the wallet: folding a client's ledger into a view
 * (available / held money, remaining package sessions, newest-first history) and
 * summarising all of a provider's clients, including the at-zero flag.
 */
@SpringBootTest
@Transactional
class WalletQueryServiceTest {

    @Autowired
    private WalletQueryService walletQueryService;

    @Autowired
    private WalletCommandService walletCommandService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletEntryRepository walletEntryRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Test
    @DisplayName("A HOLD leaves the client view with reduced available and matching held")
    void clientViewReflectsAvailableAndHeld() {
        User provider = provider("prov-view", "EUR");
        User client = linkedClient(provider, "client-view");

        walletCommandService.topUp(provider, client.getId(), new BigDecimal("100.00"), "cash");
        holdMoney(client, provider, new BigDecimal("30.00"));

        ClientWalletView view = walletQueryService.getWalletOfClient(client);

        assertThat(view.isWalletExists()).isTrue();
        assertThat(view.getCurrency()).isEqualTo("EUR");
        assertThat(view.getAvailable()).isEqualByComparingTo("70.00");
        assertThat(view.getHeld()).isEqualByComparingTo("30.00");
        assertThat(view.getHistory()).hasSize(2);
        // Newest first: the HOLD is the latest movement.
        assertThat(view.getHistory().get(0).getType()).isEqualTo(WalletEntryType.HOLD);
    }

    @Test
    @DisplayName("A granted package appears as a remaining-sessions row")
    void clientViewReflectsPackageRemainder() {
        User provider = provider("prov-pkg-view", "EUR");
        User client = linkedClient(provider, "client-pkg-view");
        Offering offering = offering(provider);

        walletCommandService.grantPackage(provider, client.getId(), offering.getId(), 10, new BigDecimal("500.00"), null);

        ClientWalletView view = walletQueryService.getWalletOfClient(client);

        assertThat(view.isHasPackages()).isTrue();
        assertThat(view.getPackages()).hasSize(1);
        assertThat(view.getPackages().get(0).getOfferingName()).isEqualTo("Consultation");
        assertThat(view.getPackages().get(0).getAvailable()).isEqualTo(10);
        assertThat(view.getPackages().get(0).getHeld()).isZero();
    }

    @Test
    @DisplayName("A client with no wallet gets an empty view in the provider currency")
    void clientWithoutWalletGetsEmptyViewInProviderCurrency() {
        User provider = provider("prov-empty", "USD");
        User client = linkedClient(provider, "client-empty");

        ClientWalletView view = walletQueryService.getWalletOfClient(client);

        assertThat(view.isWalletExists()).isFalse();
        assertThat(view.getCurrency()).isEqualTo("USD");
        assertThat(view.getAvailable()).isEqualByComparingTo("0.00");
        assertThat(view.isHasHistory()).isFalse();
    }

    @Test
    @DisplayName("Provider balances map returns available money per client and omits clients with no wallet")
    void providerBalancesReturnsAvailablePerClient() {
        User provider = provider("prov-summary", "EUR");
        User funded = linkedClient(provider, "client-funded");
        User empty = linkedClient(provider, "client-broke");

        walletCommandService.topUp(provider, funded.getId(), new BigDecimal("40.00"), null);

        Map<Long, BigDecimal> balances = walletQueryService.getClientBalances(provider);

        assertThat(balances).containsKey(funded.getId());
        assertThat(balances.get(funded.getId())).isEqualByComparingTo("40.00");
        // The client with no wallet has no ledger, so is absent from the map.
        assertThat(balances).doesNotContainKey(empty.getId());
    }

    @Test
    @DisplayName("Provider wallet view carries the client name and rejects an unlinked client")
    void providerViewCarriesClientNameAndRejectsUnlinked() {
        User provider = provider("prov-named", "EUR");
        User other = provider("prov-stranger", "EUR");
        User client = linkedClient(provider, "client-named");

        walletCommandService.topUp(provider, client.getId(), new BigDecimal("15.00"), null);

        ClientWalletView view = walletQueryService.getWalletForProvider(provider, client.getId());
        assertThat(view.getClientId()).isEqualTo(client.getId());
        assertThat(view.getClientName()).isNotBlank();
        assertThat(view.getAvailable()).isEqualByComparingTo("15.00");

        assertThatThrownBy(() -> walletQueryService.getWalletForProvider(other, client.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not linked");
    }

    // ===== helpers =====

    private void holdMoney(User client, User actor, BigDecimal amount) {
        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.HOLD, amount, null, actor, LocalDateTime.now(), null));
    }

    private User provider(String username, String currency) {
        User user = newUser(username, UserRole.PROVIDER);
        user.setCurrency(currency);
        return userRepository.save(user);
    }

    private User linkedClient(User provider, String username) {
        User client = userRepository.save(newUser(username, UserRole.CLIENT));
        providerClientLinkRepository.save(new ProviderClientLink(provider, client, LocalDateTime.now()));
        return client;
    }

    private User newUser(String username, UserRole role) {
        String unique = username + "-" + System.nanoTime();
        User user = new User();
        user.setUsername(unique);
        user.setEmail(unique + "@test.com");
        user.setPassword("123");
        user.setRole(role);
        return user;
    }

    private Offering offering(User provider) {
        return offeringRepository.save(
                new Offering(provider, "Consultation", "desc", new BigDecimal("50.00"), 60));
    }
}
