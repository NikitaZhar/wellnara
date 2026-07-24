package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.CancellationInitiator;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.AppointmentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Service tests for expiring stale appointment requests.
 *
 * <p>Expiry no longer deletes the request: the appointment is cancelled (and its
 * hold released) but kept as history.
 */
@SpringBootTest
@Transactional
class AppointmentCleanupServiceTest {

    @Autowired
    private AppointmentService appointmentService;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Test
    @DisplayName("Should expire only stale pending requests, cancelling and keeping them")
    void shouldExpireOnlyStalePendingRequests() {
        User provider = createUser("provider-cleanup", UserRole.PROVIDER);
        User client = createUser("client-cleanup", UserRole.CLIENT);
        Offering offering = createOffering(provider);

        Appointment expiredRequested = createAppointment(
                provider, client, offering, LocalDateTime.now(ZoneOffset.UTC).minusDays(1));

        Appointment expiredScheduled = createAppointment(
                provider, client, offering, LocalDateTime.now(ZoneOffset.UTC).minusDays(1));
        expiredScheduled.schedule();

        Appointment futureRequested = createAppointment(
                provider, client, offering, LocalDateTime.now(ZoneOffset.UTC).plusDays(1));

        appointmentRepository.save(expiredScheduled);

        appointmentService.expireStaleAppointmentRequests();

        Appointment expired = appointmentRepository.findById(expiredRequested.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(expired.getCancellationInitiator()).isEqualTo(CancellationInitiator.PROVIDER);

        assertThat(appointmentRepository.findById(expiredScheduled.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.SCHEDULED);
        assertThat(appointmentRepository.findById(futureRequested.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.REQUESTED);
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

    private Offering createOffering(User provider) {
        Offering offering = new Offering(
                provider,
                "Consultation",
                "Test consultation",
                new BigDecimal("100.00"),
                60
        );

        return offeringRepository.save(offering);
    }

    private Appointment createAppointment(User provider,
                                          User client,
                                          Offering offering,
                                          LocalDateTime startDateTimeUtc) {
        Appointment appointment = new Appointment(
                provider,
                client,
                offering,
                startDateTimeUtc
        );

        return appointmentRepository.save(appointment);
    }
}
