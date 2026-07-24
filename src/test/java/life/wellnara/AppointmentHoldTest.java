package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.AvailabilityDay;
import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.AvailabilityPeriodRepository;
import life.wellnara.repository.AvailabilityRuleRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.ServicePackageRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.wallet.WalletLedgerCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static java.time.DayOfWeek.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that requesting an appointment reserves funds: money is held (or a
 * package session), sufficiency is checked, and the hold is atomic with the
 * appointment.
 */
@SpringBootTest
@Transactional
class AppointmentHoldTest {

    private static final String PROVIDER_TIMEZONE = "Europe/Bratislava";
    private static final BigDecimal PRICE = new BigDecimal("100.00");

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Autowired
    private AvailabilityPeriodRepository availabilityPeriodRepository;

    @Autowired
    private AvailabilityRuleRepository availabilityRuleRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletEntryRepository walletEntryRepository;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private WalletLedgerCalculator ledgerCalculator;

    @Test
    @DisplayName("Requesting with sufficient money places a HOLD tied to the appointment")
    void moneyHoldPlacedOnRequest() {
        Fixture f = fixture("money-hold");
        Wallet wallet = topUp(f, "100.00");

        Appointment appointment = appointmentService.requestAppointment(
                f.client, f.provider.getId(), f.offering.getId(), slot(8));

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.REQUESTED);

        WalletEntry hold = onlyEntryOfType(wallet, WalletEntryType.HOLD);
        assertThat(hold.getAmount()).isEqualByComparingTo(PRICE);
        assertThat(hold.getAppointment().getId()).isEqualTo(appointment.getId());

        assertThat(available(wallet)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("A covering package is used before money")
    void packageHoldPreferredOverMoney() {
        Fixture f = fixture("package-hold");
        Wallet wallet = topUp(f, "100.00");
        ServicePackage pkg = grantPackage(f, 1);

        appointmentService.requestAppointment(
                f.client, f.provider.getId(), f.offering.getId(), slot(8));

        WalletEntry hold = onlyEntryOfType(wallet, WalletEntryType.PACKAGE_HOLD);
        assertThat(hold.getSessionCount()).isEqualTo(1);
        assertThat(hold.getServicePackage().getId()).isEqualTo(pkg.getId());

        // money untouched, package session consumed into "held"
        assertThat(available(wallet)).isEqualByComparingTo("100.00");
        assertThat(ledgerCalculator.foldSessions(
                walletEntryRepository.findAllByServicePackageOrderByIdAsc(pkg)).getAvailable()).isZero();
    }

    @Test
    @DisplayName("Insufficient funds reject the request and create no appointment")
    void insufficientFundsRejectAndCreateNoAppointment() {
        Fixture f = fixture("insufficient");
        // no wallet, no funds

        assertThatThrownBy(() -> appointmentService.requestAppointment(
                f.client, f.provider.getId(), f.offering.getId(), slot(8)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(appointmentRepository.findAllByProviderOrderByStartDateTimeUtcAsc(f.provider)).isEmpty();
    }

    @Test
    @DisplayName("A balance for one session lets the first request through and rejects the second")
    void secondRequestRejectedWhenBalanceCoversOneSession() {
        Fixture f = fixture("one-session");
        topUp(f, "100.00");

        appointmentService.requestAppointment(f.client, f.provider.getId(), f.offering.getId(), slot(8));

        assertThatThrownBy(() -> appointmentService.requestAppointment(
                f.client, f.provider.getId(), f.offering.getId(), slot(9)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(appointmentRepository.findAllByProviderOrderByStartDateTimeUtcAsc(f.provider)).hasSize(1);
    }

    // ===== fixture & helpers =====

    private record Fixture(User provider, User client, Offering offering) {
    }

    private Fixture fixture(String prefix) {
        User provider = user(prefix + "-prov", UserRole.PROVIDER);
        provider.setCurrency("EUR");
        userRepository.save(provider);

        User client = userRepository.save(user(prefix + "-client", UserRole.CLIENT));
        providerClientLinkRepository.save(new ProviderClientLink(provider, client, LocalDateTime.now()));

        Offering offering = offeringRepository.save(
                new Offering(provider, "Consultation", "desc", PRICE, 60));

        createAvailability(provider);
        return new Fixture(provider, client, offering);
    }

    private Wallet topUp(Fixture f, String amount) {
        Wallet wallet = getOrCreateWallet(f);
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.TOP_UP, new BigDecimal(amount), null, f.provider, LocalDateTime.now(), null));
        return wallet;
    }

    private ServicePackage grantPackage(Fixture f, int sessions) {
        Wallet wallet = getOrCreateWallet(f);
        ServicePackage pkg = servicePackageRepository.save(new ServicePackage(
                wallet, f.offering, sessions, PRICE, wallet.getCurrency(), f.provider, LocalDateTime.now(), null));
        walletEntryRepository.save(WalletEntry.session(
                wallet, WalletEntryType.PACKAGE_GRANT, sessions, pkg, null, f.provider, LocalDateTime.now(), null));
        return pkg;
    }

    private Wallet getOrCreateWallet(Fixture f) {
        return walletRepository.findByClient(f.client).orElseGet(() ->
                walletRepository.save(new Wallet(f.client, f.provider, "EUR", LocalDateTime.now())));
    }

    private BigDecimal available(Wallet wallet) {
        return ledgerCalculator.foldMoney(
                wallet.getCurrency(), walletEntryRepository.findAllByWalletOrderByIdAsc(wallet)).getAvailable();
    }

    private WalletEntry onlyEntryOfType(Wallet wallet, WalletEntryType type) {
        List<WalletEntry> matching = walletEntryRepository.findAllByWalletOrderByIdAsc(wallet).stream()
                .filter(entry -> entry.getType() == type)
                .toList();
        assertThat(matching).hasSize(1);
        return matching.get(0);
    }

    private LocalDateTime slot(int utcHour) {
        return LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.next(MONDAY)).atTime(utcHour, 0);
    }

    private User user(String prefix, UserRole role) {
        String username = prefix + "-" + System.nanoTime();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(role);
        return user;
    }

    private void createAvailability(User provider) {
        LocalDate anchor = LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.next(MONDAY));
        AvailabilityPeriod period = availabilityPeriodRepository.save(
                new AvailabilityPeriod(provider, anchor, anchor.plusDays(30), PROVIDER_TIMEZONE));
        availabilityRuleRepository.save(
                new AvailabilityRule(period, AvailabilityDay.MONDAY, LocalTime.of(9, 0), LocalTime.of(13, 0)));
    }
}
