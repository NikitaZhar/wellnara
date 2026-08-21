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
import life.wellnara.service.WalletQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import life.wellnara.dto.ClientPackageView;
import life.wellnara.dto.ClientWalletView;

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
    private WalletQueryService walletQueryService;

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
    @DisplayName("Selling a package creates a ServicePackage and a PACKAGE_GRANT entry")
    void sellPackageCreatesPackageAndEntry() {
        User provider = provider("prov-pkg", "EUR");
        User client = linkedClient(provider, "client-pkg");
        Offering offering = packageableOffering(provider);

        walletCommandService.sellPackage(provider, client.getId(), offering.getId(), 10, new BigDecimal("400.00"), "intro");

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
    @DisplayName("A package cannot be sold for another provider's offering")
    void sellPackageRejectsForeignOffering() {
        User provider = provider("prov-own", "EUR");
        User other = provider("prov-other", "EUR");
        User client = linkedClient(provider, "client-foreign");
        Offering foreignOffering = packageableOffering(other);

        assertThatThrownBy(() ->
                walletCommandService.sellPackage(
                        provider, client.getId(), foreignOffering.getId(), 5, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Offering");
    }

    @Test
    @DisplayName("A non-packageable service cannot be sold as a package")
    void sellPackageRejectsNonPackageableOffering() {
        User provider = provider("prov-nonpkg", "EUR");
        User client = linkedClient(provider, "client-nonpkg");
        Offering offering = offering(provider);

        assertThatThrownBy(() ->
                walletCommandService.sellPackage(provider, client.getId(), offering.getId(), 5, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not sold as a package");
    }

    @Test
    @DisplayName("Package session count must be within the offering's min/max")
    void sellPackageRejectsSessionsOutOfRange() {
        User provider = provider("prov-range", "EUR");
        User client = linkedClient(provider, "client-range");
        Offering offering = packageableOffering(provider);

        assertThatThrownBy(() ->
                walletCommandService.sellPackage(provider, client.getId(), offering.getId(), 2, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 3 and 10");
    }

    @Test
    @DisplayName("Requesting a package holds the price and grants no sessions until approved")
    void requestPackageHoldsMoneyPendingApproval() {
        User provider = provider("prov-req", "EUR");
        User client = linkedClient(provider, "client-req");
        Offering offering = packageableOffering(provider);
        walletCommandService.topUp(provider, client.getId(), new BigDecimal("500.00"), null);

        walletCommandService.requestPackage(client, offering.getId(), 10, null);

        ClientWalletView view = walletQueryService.getWalletOfClient(client);
        assertThat(view.getAvailable()).isEqualByComparingTo("100.00");
        assertThat(view.getHeld()).isEqualByComparingTo("400.00");
        assertThat(walletQueryService.getActivePackagesOfClient(client)).isEmpty();
        assertThat(walletQueryService.getPendingPackageRequestsForProvider(provider)).hasSize(1);
    }

    @Test
    @DisplayName("Approving a package settles the held price and grants the sessions")
    void acceptPackageGrantsSessionsAndSettles() {
        User provider = provider("prov-acc", "EUR");
        User client = linkedClient(provider, "client-acc");
        Offering offering = packageableOffering(provider);
        walletCommandService.topUp(provider, client.getId(), new BigDecimal("500.00"), null);
        walletCommandService.requestPackage(client, offering.getId(), 10, null);
        Long packageId = onlyPackageId(client);

        walletCommandService.acceptPackageRequest(provider, packageId);

        ClientWalletView view = walletQueryService.getWalletOfClient(client);
        assertThat(view.getAvailable()).isEqualByComparingTo("100.00");
        assertThat(view.getHeld()).isEqualByComparingTo("0.00");
        List<ClientPackageView> packages = walletQueryService.getActivePackagesOfClient(client);
        assertThat(packages).hasSize(1);
        assertThat(packages.get(0).getRemaining()).isEqualTo(10);
    }

    @Test
    @DisplayName("Declining a package releases the held price and grants nothing")
    void rejectPackageReleasesMoney() {
        User provider = provider("prov-rej", "EUR");
        User client = linkedClient(provider, "client-rej");
        Offering offering = packageableOffering(provider);
        walletCommandService.topUp(provider, client.getId(), new BigDecimal("500.00"), null);
        walletCommandService.requestPackage(client, offering.getId(), 10, null);
        Long packageId = onlyPackageId(client);

        walletCommandService.rejectPackageRequest(provider, packageId);

        assertThat(walletQueryService.getWalletOfClient(client).getAvailable()).isEqualByComparingTo("500.00");
        assertThat(walletQueryService.getActivePackagesOfClient(client)).isEmpty();
    }

    @Test
    @DisplayName("Requesting a package is rejected when the wallet lacks the funds")
    void requestPackageRejectsInsufficientFunds() {
        User provider = provider("prov-req-poor", "EUR");
        User client = linkedClient(provider, "client-req-poor");
        Offering offering = packageableOffering(provider);
        walletCommandService.topUp(provider, client.getId(), new BigDecimal("100.00"), null);

        assertThatThrownBy(() -> walletCommandService.requestPackage(client, offering.getId(), 10, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");
    }

    @Test
    @DisplayName("Refund credits the wallet and voids the package's unused sessions")
    void refundCreditsWalletAndVoidsSessions() {
        User provider = provider("prov-refund", "EUR");
        User client = linkedClient(provider, "client-refund");
        Offering offering = packageableOffering(provider);
        walletCommandService.sellPackage(provider, client.getId(), offering.getId(), 10, new BigDecimal("400.00"), null);

        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        Long packageId = servicePackageRepository.findAllByWallet(wallet).get(0).getId();

        walletCommandService.refundPackage(provider, packageId, new BigDecimal("400.00"), "refund");

        assertThat(walletQueryService.getWalletOfClient(client).getAvailable()).isEqualByComparingTo("400.00");
        assertThat(walletQueryService.getActivePackagesOfClient(client)).isEmpty();
    }

    @Test
    @DisplayName("A package can only be refunded by the provider that owns the wallet")
    void refundRejectsForeignProvider() {
        User provider = provider("prov-refund-own", "EUR");
        User other = provider("prov-refund-other", "EUR");
        User client = linkedClient(provider, "client-refund-own");
        Offering offering = packageableOffering(provider);
        walletCommandService.sellPackage(provider, client.getId(), offering.getId(), 5, null, null);

        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        Long packageId = servicePackageRepository.findAllByWallet(wallet).get(0).getId();

        assertThatThrownBy(() -> walletCommandService.refundPackage(other, packageId, new BigDecimal("100.00"), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong to provider");
    }

    // ===== helpers =====

    private Long onlyPackageId(User client) {
        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        return servicePackageRepository.findAllByWallet(wallet).get(0).getId();
    }

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

    private Offering packageableOffering(User provider) {
        Offering offering = new Offering(provider, "Massage", "desc", new BigDecimal("50.00"), 60);
        offering.setPackagePricePerSession(new BigDecimal("40.00"));
        offering.setMinPackageSessions(3);
        offering.setMaxPackageSessions(10);
        return offeringRepository.save(offering);
    }
}
