package life.wellnara;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
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
 * Service tests for expired appointment cleanup.
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
    @DisplayName("Should delete only expired unpaid appointments")
    void shouldDeleteOnlyExpiredUnpaidAppointments() {
        User provider = createUser("provider-cleanup", UserRole.PROVIDER);
        User client = createUser("client-cleanup", UserRole.CLIENT);
        Offering offering = createOffering(provider);

        Appointment expiredRequested = createAppointment(
                provider,
                client,
                offering,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
        );

        Appointment expiredPaymentRequested = createAppointment(
                provider,
                client,
                offering,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
        );
        expiredPaymentRequested.requestPayment();

        Appointment expiredConfirmed = createAppointment(
                provider,
                client,
                offering,
                LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
        );
        expiredConfirmed.confirm();

        Appointment futureRequested = createAppointment(
                provider,
                client,
                offering,
                LocalDateTime.now(ZoneOffset.UTC).plusDays(1)
        );

        appointmentRepository.save(expiredPaymentRequested);
        appointmentRepository.save(expiredConfirmed);

        appointmentService.deleteExpiredUnpaidAppointments();

        assertThat(appointmentRepository.findById(expiredRequested.getId())).isEmpty();
        assertThat(appointmentRepository.findById(expiredPaymentRequested.getId())).isEmpty();

        assertThat(appointmentRepository.findById(expiredConfirmed.getId())).isPresent();
        assertThat(appointmentRepository.findById(futureRequested.getId())).isPresent();
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