package life.wellnara;

import java.util.List;

import life.wellnara.dto.ClientPackageView;
import life.wellnara.dto.ClientWalletView;
import life.wellnara.dto.HeldItemView;
import life.wellnara.dto.WalletHistoryRow;
import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.CancellationInitiator;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.AppointmentRepository;
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
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

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

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private MessageSource messageSource;

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
        // The hold shows only in the held balance; the client's own history hides
        // reservation churn, so only the top-up movement remains.
        assertThat(view.getHistory()).hasSize(1);
        assertThat(view.getHistory().get(0).getType()).isEqualTo(WalletEntryType.TOP_UP);
    }

    @Test
    @DisplayName("A hold and its release net to zero and are absent from the client history")
    void clientHistoryHidesReservationChurn() {
        User provider = provider("prov-churn", "EUR");
        User client = linkedClient(provider, "client-churn");

        walletCommandService.topUp(provider, client.getId(), new BigDecimal("100.00"), null);
        holdMoney(client, provider, new BigDecimal("30.00"));
        releaseMoney(client, provider, new BigDecimal("30.00"));

        ClientWalletView view = walletQueryService.getWalletOfClient(client);

        assertThat(view.getAvailable()).isEqualByComparingTo("100.00");
        assertThat(view.getHeld()).isEqualByComparingTo("0.00");
        assertThat(view.getHistory()).hasSize(1);
        assertThat(view.getHistory().get(0).getType()).isEqualTo(WalletEntryType.TOP_UP);
    }

    @Test
    @DisplayName("Held breakdown lists the appointment a hold reserves against and omits released holds")
    void heldBreakdownListsActiveHolds() {
        User provider = provider("prov-held", "EUR");
        User client = linkedClient(provider, "client-held");
        Offering offering = offering(provider);

        walletCommandService.topUp(provider, client.getId(), new BigDecimal("100.00"), null);

        Appointment active = appointment(provider, client, offering);
        holdMoneyFor(client, provider, active, new BigDecimal("50.00"));

        Appointment released = appointment(provider, client, offering);
        holdMoneyFor(client, provider, released, new BigDecimal("30.00"));
        releaseMoneyFor(client, provider, released, new BigDecimal("30.00"));

        List<HeldItemView> held = walletQueryService.getHeldBreakdownOfClient(client);

        assertThat(held).hasSize(1);
        assertThat(held.get(0).getOfferingName()).isEqualTo("Consultation");
        assertThat(held.get(0).getAmount()).isEqualByComparingTo("50.00");
        assertThat(held.get(0).getStartDateTime()).isNotNull();
    }

    @Test
    @DisplayName("An appointment settlement is labelled by the appointment outcome")
    void settleLabelReflectsAppointmentOutcome() {
        User provider = provider("prov-settle", "EUR");
        User client = linkedClient(provider, "client-settle");
        Offering offering = offering(provider);

        walletCommandService.topUp(provider, client.getId(), new BigDecimal("100.00"), null);
        settle(client, provider, terminalAppointment(provider, client, offering, AppointmentStatus.COMPLETED), new BigDecimal("10.00"));
        settle(client, provider, terminalAppointment(provider, client, offering, AppointmentStatus.NO_SHOW), new BigDecimal("20.00"));
        settle(client, provider, terminalAppointment(provider, client, offering, AppointmentStatus.CANCELLED), new BigDecimal("30.00"));

        Map<String, String> labelByAmount = walletQueryService.getWalletOfClient(client).getHistory().stream()
                .filter(row -> row.getType() == WalletEntryType.SETTLE)
                .collect(Collectors.toMap(row -> row.getAmount().toPlainString(), WalletHistoryRow::getTypeLabel));

        assertThat(labelByAmount.get("10.00")).isEqualTo(msg("wallet.entryType.settle.completed"));
        assertThat(labelByAmount.get("20.00")).isEqualTo(msg("wallet.entryType.settle.noShow"));
        assertThat(labelByAmount.get("30.00")).isEqualTo(msg("wallet.entryType.settle.lateCancel"));
    }

    @Test
    @DisplayName("A granted package appears as a remaining-sessions row")
    void clientViewReflectsPackageRemainder() {
        User provider = provider("prov-pkg-view", "EUR");
        User client = linkedClient(provider, "client-pkg-view");
        Offering offering = offering(provider);

        walletCommandService.topUp(provider, client.getId(), new BigDecimal("1000.00"), null);
        Long packageId = walletCommandService.requestPackage(
                client, offering.getId(), 10, LocalDateTime.of(2026, 6, 1, 10, 0), null).getId();
        walletCommandService.acceptPackageRequest(provider, packageId);

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

    private void releaseMoney(User client, User actor, BigDecimal amount) {
        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.RELEASE, amount, null, actor, LocalDateTime.now(), null));
    }

    private Appointment appointment(User provider, User client, Offering offering) {
        return appointmentRepository.save(
                new Appointment(provider, client, offering, LocalDateTime.of(2026, 6, 1, 10, 0)));
    }

    private void holdMoneyFor(User client, User actor, Appointment appointment, BigDecimal amount) {
        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.HOLD, amount, appointment, actor, LocalDateTime.now(), null));
    }

    private void releaseMoneyFor(User client, User actor, Appointment appointment, BigDecimal amount) {
        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.RELEASE, amount, appointment, actor, LocalDateTime.now(), null));
    }

    private void settle(User client, User actor, Appointment appointment, BigDecimal amount) {
        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.SETTLE, amount, appointment, actor, LocalDateTime.now(), null));
    }

    private Appointment terminalAppointment(User provider, User client, Offering offering, AppointmentStatus status) {
        Appointment appointment = new Appointment(provider, client, offering, LocalDateTime.of(2026, 6, 1, 10, 0));
        switch (status) {
            case COMPLETED -> { appointment.schedule(); appointment.complete(); }
            case NO_SHOW -> { appointment.schedule(); appointment.markNoShow(); }
            case CANCELLED -> appointment.cancel(CancellationInitiator.CLIENT, null, LocalDateTime.now());
            default -> throw new IllegalArgumentException("Not a settleable terminal status: " + status);
        }
        return appointmentRepository.save(appointment);
    }

    private String msg(String key) {
        return messageSource.getMessage(key, null, LocaleContextHolder.getLocale());
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

    @Test
    @DisplayName("Active packages aggregate multiple grants of the same offering into one row")
    void activePackagesAggregateByOffering() {
        User provider = provider("prov-agg", "EUR");
        User client = linkedClient(provider, "client-agg");
        Offering offering = offering(provider);

        walletCommandService.topUp(provider, client.getId(), new BigDecimal("1000.00"), null);
        Long firstPackageId = walletCommandService.requestPackage(
                client, offering.getId(), 10, LocalDateTime.of(2026, 6, 1, 10, 0), null).getId();
        walletCommandService.acceptPackageRequest(provider, firstPackageId);
        Long secondPackageId = walletCommandService.requestPackage(
                client, offering.getId(), 5, LocalDateTime.of(2026, 6, 2, 10, 0), null).getId();
        walletCommandService.acceptPackageRequest(provider, secondPackageId);

        List<ClientPackageView> packages = walletQueryService.getActivePackagesOfClient(client);

        assertThat(packages).hasSize(1);
        ClientPackageView view = packages.get(0);
        assertThat(view.getOfferingId()).isEqualTo(offering.getId());
        assertThat(view.getTotal()).isEqualTo(15);
        assertThat(view.getRemaining()).isEqualTo(15);
        assertThat(view.getPending()).isZero();
        assertThat(view.canBookNext()).isTrue();
    }

    @Test
    @DisplayName("A client with no wallet has no active packages")
    void activePackagesEmptyWithoutWallet() {
        User client = userRepository.save(newUser("client-no-wallet", UserRole.CLIENT));

        assertThat(walletQueryService.getActivePackagesOfClient(client)).isEmpty();
    }

    @Test
    @DisplayName("Package labels are empty when no appointments are given")
    void packageLabelsEmptyForNoAppointments() {
        assertThat(walletQueryService.packageLabelsForAppointments(List.of())).isEmpty();
    }

    private Offering offering(User provider) {
        Offering offering = new Offering(provider, "Consultation", "desc", new BigDecimal("50.00"), 60);
        offering.setPackagePricePerSession(new BigDecimal("40.00"));
        return offeringRepository.save(offering);
    }
}
