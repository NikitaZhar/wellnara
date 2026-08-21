package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.AvailabilityDay;
import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.AvailabilityPeriodRepository;
import life.wellnara.repository.AvailabilityRuleRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.AppointmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

import static java.time.DayOfWeek.MONDAY;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the provider-only preparation / wrap-up buffers in the
 * availability engine.
 *
 * <p>All scenarios use a Monday 09:00–13:00 provider-local availability window
 * (Europe/Bratislava) and a 60-minute offering. Booking start times are given
 * in provider-local time and converted to UTC via {@link #utc}, so the suite is
 * independent of daylight-saving offsets; {@code getBookableTimes} itself
 * returns provider-local times, which is what the assertions compare against.
 */
@SpringBootTest
@Transactional
class AppointmentBufferAvailabilityTest {

    private static final String PROVIDER_TIMEZONE = "Europe/Bratislava";

    @Autowired
    private AppointmentService appointmentService;

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

    @Test
    @DisplayName("Wrap-up buffer blocks a back-to-back booking but allows one after the gap")
    void wrapBufferBlocksBackToBackBooking() {
        User provider = createProvider("provider-wrap-block");
        User first = createClient("client-wrap-first");
        User second = createClient("client-wrap-second");
        User third = createClient("client-wrap-third");
        linkClient(provider, first);
        linkClient(provider, second);
        linkClient(provider, third);

        Offering offering = createOffering(provider, 60, 0, 15);
        createAvailability(provider, LocalTime.of(9, 0), LocalTime.of(13, 0));

        // Session 09:00–10:00 local; wrap-up reserves until 10:15.
        appointmentService.requestAppointment(first, provider.getId(), offering.getId(), utc(9, 0));

        // 10:00 is back-to-back with no gap for the wrap-up — rejected.
        assertThatThrownBy(() -> appointmentService.requestAppointment(
                second, provider.getId(), offering.getId(), utc(10, 0)))
                .hasMessageContaining("Time slot is already booked");

        // 10:15 clears the 15-minute wrap-up — allowed.
        Appointment allowed = appointmentService.requestAppointment(
                third, provider.getId(), offering.getId(), utc(10, 15));

        assertThat(allowed).isNotNull();
        assertThat(allowed.getStatus()).isEqualTo(AppointmentStatus.REQUESTED);
    }

    @Test
    @DisplayName("Wrap-up buffer removes the following slots and still allows the last session that fits")
    void wrapBufferRemovesFollowingSlotsButKeepsLastFittingStart() {
        User provider = createProvider("provider-wrap-slots");
        User client = createClient("client-wrap-slots");
        linkClient(provider, client);

        Offering offering = createOffering(provider, 60, 0, 15);
        createAvailability(provider, LocalTime.of(9, 0), LocalTime.of(13, 0));

        appointmentService.requestAppointment(client, provider.getId(), offering.getId(), utc(9, 0));

        List<LocalTime> times = appointmentService.getBookableTimes(provider, offering, nextMonday());

        assertThat(times)
                // session + wrap-up occupy 09:00–10:15
                .doesNotContain(LocalTime.of(9, 15), LocalTime.of(9, 30), LocalTime.of(9, 45), LocalTime.of(10, 0))
                // first free start once the wrap-up gap clears
                .contains(LocalTime.of(10, 15))
                // 12:00 still bookable: the session fits to 13:00 and the wrap-up
                // is allowed to spill past the window end
                .contains(LocalTime.of(12, 0))
                // 12:15 would push the session end past 13:00 — does not fit
                .doesNotContain(LocalTime.of(12, 15));
    }

    @Test
    @DisplayName("Prep buffer spills before the window start, so the first session still starts at the window start")
    void prepBufferDoesNotShrinkTheWindow() {
        User provider = createProvider("provider-prep-window");
        User client = createClient("client-prep-window");
        linkClient(provider, client);

        Offering offering = createOffering(provider, 60, 15, 0);
        createAvailability(provider, LocalTime.of(9, 0), LocalTime.of(13, 0));

        List<LocalTime> times = appointmentService.getBookableTimes(provider, offering, nextMonday());

        // 09:00 stays bookable although the 15-minute prep falls before the
        // window — the window is measured by the session duration alone.
        assertThat(times)
                .contains(LocalTime.of(9, 0))
                .contains(LocalTime.of(12, 0))
                .doesNotContain(LocalTime.of(12, 15));
    }

    @Test
    @DisplayName("Each appointment reserves its own offering's buffer, regardless of the offering being booked next")
    void existingAppointmentUsesItsOwnBuffer() {
        User provider = createProvider("provider-mixed-buffers");
        User first = createClient("client-mixed-first");
        User second = createClient("client-mixed-second");
        linkClient(provider, first);
        linkClient(provider, second);

        Offering longWrap = createOffering(provider, 60, 0, 30);
        Offering noBuffer = createOffering(provider, 60, 0, 0);
        createAvailability(provider, LocalTime.of(9, 0), LocalTime.of(13, 0));

        // Session 09:00–10:00 with a 30-minute wrap-up reserves until 10:30.
        appointmentService.requestAppointment(first, provider.getId(), longWrap.getId(), utc(9, 0));

        List<LocalTime> times = appointmentService.getBookableTimes(provider, noBuffer, nextMonday());

        // Even though the offering being booked has no buffers, the earlier
        // appointment's own 30-minute wrap-up defines the gap.
        assertThat(times)
                .doesNotContain(LocalTime.of(10, 0), LocalTime.of(10, 15))
                .contains(LocalTime.of(10, 30));
    }

    // ===== fixtures =====

    private static LocalDate nextMonday() {
        return LocalDate.now(ZoneOffset.UTC).with(TemporalAdjusters.next(MONDAY));
    }

    /**
     * Provider-local time on the next Monday, expressed as the equivalent UTC
     * {@code LocalDateTime} that {@code requestAppointment} expects.
     */
    private LocalDateTime utc(int hour, int minute) {
        return nextMonday().atTime(hour, minute)
                .atZone(ZoneId.of(PROVIDER_TIMEZONE))
                .withZoneSameInstant(ZoneOffset.UTC)
                .toLocalDateTime();
    }

    private User createProvider(String usernamePrefix) {
        return createUser(usernamePrefix, UserRole.PROVIDER);
    }

    private User createClient(String usernamePrefix) {
        return createUser(usernamePrefix, UserRole.CLIENT);
    }

    private User createUser(String usernamePrefix, UserRole role) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(role);

        return userRepository.save(user);
    }

    private void linkClient(User provider, User client) {
        providerClientLinkRepository.save(
                new ProviderClientLink(provider, client, LocalDateTime.now()));
        Wallet wallet = walletRepository.save(new Wallet(client, provider, "EUR", LocalDateTime.now()));
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.TOP_UP, new BigDecimal("100000.00"), null, provider, LocalDateTime.now(), null));
    }

    private Offering createOffering(User provider, int durationMinutes, int prepMinutes, int wrapMinutes) {
        Offering offering = new Offering(
                provider,
                "Buffered offering",
                "desc",
                new BigDecimal("100.00"),
                durationMinutes,
                prepMinutes,
                wrapMinutes);

        return offeringRepository.save(offering);
    }

    private void createAvailability(User provider, LocalTime startTime, LocalTime endTime) {
        LocalDate anchor = nextMonday();

        AvailabilityPeriod period = availabilityPeriodRepository.save(
                new AvailabilityPeriod(provider, anchor, anchor.plusDays(30), PROVIDER_TIMEZONE));

        availabilityRuleRepository.save(
                new AvailabilityRule(period, AvailabilityDay.MONDAY, startTime, endTime));
    }
}
