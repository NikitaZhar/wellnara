package life.wellnara;

import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.ServicePackageRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.WalletCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for provider money-in wallet operations: top-up, package grant, wallet
 * creation on first use, and service-layer role/ownership checks.
 */
@SpringBootTest
@Transactional
class WalletCommandServiceTest {

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
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Test
    @DisplayName("Top-up creates the client's wallet on first use and appends a TOP_UP entry")
    void topUpCreatesWalletAndEntry() {
        User provider = provider("prov-topup", "EUR");
        User client = linkedClient(provider, "client-topup");

        walletCommandService.topUp(provider, client.getId(), new BigDecimal("100.00"), "cash");

        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        assertThat(wallet.getProvider().getId()).isEqualTo(provider.getId());
        assertThat(wallet.getCurrency()).isEqualTo("EUR");

        List<WalletEntry> entries = walletEntryRepository.findAllByWalletOrderByIdAsc(wallet);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getType()).isEqualTo(WalletEntryType.TOP_UP);
        assertThat(entries.get(0).getAmount()).isEqualByComparingTo("100.00");
        assertThat(entries.get(0).getCreatedBy().getId()).isEqualTo(provider.getId());
    }

    @Test
    @DisplayName("A second top-up reuses the same wallet")
    void secondTopUpReusesWallet() {
        User provider = provider("prov-reuse", "EUR");
        User client = linkedClient(provider, "client-reuse");

        walletCommandService.topUp(provider, client.getId(), new BigDecimal("50.00"), null);
        walletCommandService.topUp(provider, client.getId(), new BigDecimal("20.00"), null);

        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        assertThat(walletEntryRepository.findAllByWalletOrderByIdAsc(wallet)).hasSize(2);
    }

    @Test
    @DisplayName("Granting a package creates a ServicePackage and a PACKAGE_GRANT entry")
    void grantPackageCreatesPackageAndEntry() {
        User provider = provider("prov-pkg", "EUR");
        User client = linkedClient(provider, "client-pkg");
        Offering offering = offering(provider);

        walletCommandService.grantPackage(provider, client.getId(), offering.getId(), 10, new BigDecimal("500.00"), "intro");

        Wallet wallet = walletRepository.findByClient(client).orElseThrow();

        List<ServicePackage> packages = servicePackageRepository.findAllByWallet(wallet);
        assertThat(packages).hasSize(1);
        assertThat(packages.get(0).getTotalSessions()).isEqualTo(10);
        assertThat(packages.get(0).getCurrency()).isEqualTo("EUR");

        List<WalletEntry> entries = walletEntryRepository.findAllByWalletOrderByIdAsc(wallet);
        assertThat(entries).hasSize(1);
        assertThat(entries.get(0).getType()).isEqualTo(WalletEntryType.PACKAGE_GRANT);
        assertThat(entries.get(0).getSessionCount()).isEqualTo(10);
        assertThat(entries.get(0).getServicePackage().getId()).isEqualTo(packages.get(0).getId());
    }

    @Test
    @DisplayName("A client cannot top up a wallet (role checked on the service layer)")
    void clientCannotTopUp() {
        User provider = provider("prov-role", "EUR");
        User client = linkedClient(provider, "client-role");

        assertThatThrownBy(() ->
                walletCommandService.topUp(client, client.getId(), new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("provider");
    }

    @Test
    @DisplayName("Top-up is rejected for a client not linked to the provider")
    void topUpRejectedForUnlinkedClient() {
        User provider = provider("prov-unlinked", "EUR");
        User stranger = client("client-unlinked");

        assertThatThrownBy(() ->
                walletCommandService.topUp(provider, stranger.getId(), new BigDecimal("10.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not linked");
    }

    @Test
    @DisplayName("Top-up amount must be positive")
    void topUpAmountMustBePositive() {
        User provider = provider("prov-amount", "EUR");
        User client = linkedClient(provider, "client-amount");

        assertThatThrownBy(() ->
                walletCommandService.topUp(provider, client.getId(), new BigDecimal("0.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
    }

    @Test
    @DisplayName("A package cannot be granted for another provider's offering")
    void grantPackageRejectsForeignOffering() {
        User provider = provider("prov-own", "EUR");
        User other = provider("prov-other", "EUR");
        User client = linkedClient(provider, "client-foreign");
        Offering foreignOffering = offering(other);

        assertThatThrownBy(() ->
                walletCommandService.grantPackage(
                        provider, client.getId(), foreignOffering.getId(), 5, new BigDecimal("100.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Offering");
    }

    // ===== helpers =====

    private User provider(String username, String currency) {
        User user = newUser(username, UserRole.PROVIDER);
        user.setCurrency(currency);
        return userRepository.save(user);
    }

    private User client(String username) {
        return userRepository.save(newUser(username, UserRole.CLIENT));
    }

    private User linkedClient(User provider, String username) {
        User client = client(username);
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
