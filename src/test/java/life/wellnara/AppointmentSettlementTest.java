package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.CancellationInitiator;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.ServicePackageRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.AppointmentSettlementService;
import life.wellnara.service.wallet.WalletLedgerCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the settlement rules table: which terminal transitions release the hold and
 * which settle it, the exact 24-hour client-cancellation boundary, idempotency of a
 * repeated finalisation, and the package (session) path.
 *
 * <p>A fixed clock anchors "now" so the 24-hour boundary is exact. Appointments are
 * set up with their hold directly (no availability/calendar) to keep each test focused
 * on settlement.
 */
@SpringBootTest
@Transactional
@Import(AppointmentSettlementTest.FixedClockConfig.class)
class AppointmentSettlementTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final LocalDateTime NOW_UTC = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    private static final BigDecimal PRICE = new BigDecimal("100.00");

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentSettlementService settlementService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletEntryRepository walletEntryRepository;

    @Autowired
    private ServicePackageRepository servicePackageRepository;

    @Autowired
    private WalletLedgerCalculator ledgerCalculator;

    @Test
    @DisplayName("Provider rejecting a request releases the hold")
    void rejectReleases() {
        Held h = heldMoney("reject", AppointmentStatus.REQUESTED, NOW_UTC.plusDays(3));

        appointmentService.rejectAppointment(h.provider, h.appointment.getId(), "not suitable");

        assertFinalType(h.wallet, WalletEntryType.RELEASE);
        assertThat(available(h.wallet)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Provider cancelling a scheduled appointment releases the hold")
    void providerCancelReleases() {
        Held h = heldMoney("prov-cancel", AppointmentStatus.SCHEDULED, NOW_UTC.plusDays(3));

        appointmentService.cancelScheduledAppointment(h.provider, h.appointment.getId());

        assertFinalType(h.wallet, WalletEntryType.RELEASE);
        assertThat(available(h.wallet)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Completing a scheduled appointment settles the hold")
    void completeSettles() {
        Held h = heldMoney("complete", AppointmentStatus.SCHEDULED, NOW_UTC.plusDays(3));

        appointmentService.completeScheduledAppointment(h.provider, h.appointment.getId());

        assertFinalType(h.wallet, WalletEntryType.SETTLE);
        assertThat(available(h.wallet)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Marking a no-show settles the hold")
    void noShowSettles() {
        Held h = heldMoney("noshow", AppointmentStatus.SCHEDULED, NOW_UTC.plusDays(3));

        appointmentService.markAppointmentNoShow(h.provider, h.appointment.getId());

        assertFinalType(h.wallet, WalletEntryType.SETTLE);
        assertThat(available(h.wallet)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Client cancelling exactly 24h before start releases the hold")
    void clientCancelAtBoundaryReleases() {
        Held h = heldMoney("client-24h", AppointmentStatus.SCHEDULED, NOW_UTC.plusHours(24));

        appointmentService.cancelScheduledAppointmentByClient(h.client, h.appointment.getId());

        assertFinalType(h.wallet, WalletEntryType.RELEASE);
        assertThat(available(h.wallet)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Client cancelling less than 24h before start settles the hold")
    void clientCancelWithin24hSettles() {
        Held h = heldMoney("client-23h", AppointmentStatus.SCHEDULED, NOW_UTC.plusHours(23));

        appointmentService.cancelScheduledAppointmentByClient(h.client, h.appointment.getId());

        assertFinalType(h.wallet, WalletEntryType.SETTLE);
        assertThat(available(h.wallet)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Client cancelling a pending request releases the hold and keeps the appointment")
    void cancelPendingReleasesAndKeeps() {
        Held h = heldMoney("pending", AppointmentStatus.REQUESTED, NOW_UTC.plusDays(3));

        appointmentService.cancelPendingAppointmentByClient(h.client, h.appointment.getId());

        Appointment saved = appointmentRepository.findById(h.appointment.getId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(saved.getCancellationInitiator()).isEqualTo(CancellationInitiator.CLIENT);
        assertFinalType(h.wallet, WalletEntryType.RELEASE);
        assertThat(available(h.wallet)).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Settlement is idempotent: repeating it adds no second final entry")
    void settlementIsIdempotent() {
        Held h = heldMoney("idempotent", AppointmentStatus.SCHEDULED, NOW_UTC.plusDays(3));

        appointmentService.completeScheduledAppointment(h.provider, h.appointment.getId());
        // replay the settle directly
        settlementService.settle(appointmentRepository.findById(h.appointment.getId()).orElseThrow(), h.provider);
        settlementService.release(appointmentRepository.findById(h.appointment.getId()).orElseThrow(), h.provider);

        long settleCount = walletEntryRepository.findAllByWalletOrderByIdAsc(h.wallet).stream()
                .filter(entry -> entry.getType() == WalletEntryType.SETTLE)
                .count();
        assertThat(settleCount).isEqualTo(1);
        assertThat(available(h.wallet)).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("Releasing a package hold returns the session")
    void packageHoldReleaseReturnsSession() {
        Held h = heldPackage("pkg-release", NOW_UTC.plusDays(3));

        appointmentService.rejectAppointment(h.provider, h.appointment.getId(), "not suitable");

        assertFinalType(h.wallet, WalletEntryType.PACKAGE_RELEASE);
        assertThat(ledgerCalculator.foldSessions(
                walletEntryRepository.findAllByServicePackageOrderByIdAsc(h.servicePackage)).getAvailable())
                .isEqualTo(1);
    }

    // ===== helpers =====

    private static final class Held {
        private final User provider;
        private final User client;
        private final Appointment appointment;
        private final Wallet wallet;
        private final ServicePackage servicePackage;

        private Held(User provider, User client, Appointment appointment, Wallet wallet, ServicePackage servicePackage) {
            this.provider = provider;
            this.client = client;
            this.appointment = appointment;
            this.wallet = wallet;
            this.servicePackage = servicePackage;
        }
    }

    private Held heldMoney(String prefix, AppointmentStatus status, LocalDateTime startUtc) {
        User provider = provider(prefix);
        User client = linkedClient(provider, prefix);
        Offering offering = offering(provider);
        Wallet wallet = walletRepository.save(new Wallet(client, provider, "EUR", NOW_UTC));
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.TOP_UP, PRICE, null, provider, NOW_UTC, null));

        Appointment appointment = appointment(provider, client, offering, status, startUtc);
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.HOLD, PRICE, appointment, client, NOW_UTC, null));
        return new Held(provider, client, appointment, wallet, null);
    }

    private Held heldPackage(String prefix, LocalDateTime startUtc) {
        User provider = provider(prefix);
        User client = linkedClient(provider, prefix);
        Offering offering = offering(provider);
        Wallet wallet = walletRepository.save(new Wallet(client, provider, "EUR", NOW_UTC));
        ServicePackage pkg = servicePackageRepository.save(new ServicePackage(
                wallet, offering, 1, PRICE, "EUR", provider, NOW_UTC, null));
        walletEntryRepository.save(WalletEntry.session(
                wallet, WalletEntryType.PACKAGE_GRANT, 1, pkg, null, provider, NOW_UTC, null));

        Appointment appointment = appointment(provider, client, offering, AppointmentStatus.REQUESTED, startUtc);
        walletEntryRepository.save(WalletEntry.session(
                wallet, WalletEntryType.PACKAGE_HOLD, 1, pkg, appointment, client, NOW_UTC, null));
        return new Held(provider, client, appointment, wallet, pkg);
    }

    private Appointment appointment(User provider, User client, Offering offering,
                                    AppointmentStatus status, LocalDateTime startUtc) {
        Appointment appointment = new Appointment(provider, client, offering, startUtc);
        if (status == AppointmentStatus.SCHEDULED) {
            appointment.schedule();
        }
        return appointmentRepository.save(appointment);
    }

    private void assertFinalType(Wallet wallet, WalletEntryType expected) {
        List<WalletEntry> finals = walletEntryRepository.findAllByWalletOrderByIdAsc(wallet).stream()
                .filter(entry -> entry.getType() == expected)
                .toList();
        assertThat(finals).hasSize(1);
    }

    private BigDecimal available(Wallet wallet) {
        return ledgerCalculator.foldMoney(
                wallet.getCurrency(), walletEntryRepository.findAllByWalletOrderByIdAsc(wallet)).getAvailable();
    }

    private User provider(String prefix) {
        User user = user(prefix + "-prov", UserRole.PROVIDER);
        user.setCurrency("EUR");
        return userRepository.save(user);
    }

    private User linkedClient(User provider, String prefix) {
        User client = userRepository.save(user(prefix + "-client", UserRole.CLIENT));
        providerClientLinkRepository.save(new ProviderClientLink(provider, client, NOW_UTC));
        return client;
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

    private Offering offering(User provider) {
        return offeringRepository.save(new Offering(provider, "Consultation", "desc", PRICE, 60));
    }

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW, ZoneOffset.UTC);
        }
    }
}
