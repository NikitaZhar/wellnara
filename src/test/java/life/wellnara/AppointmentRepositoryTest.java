package life.wellnara;

import life.wellnara.model.*;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Repository tests for Appointment entity.
 */
@DataJpaTest
class AppointmentRepositoryTest {

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    /**
     * Verifies that appointment is created with REQUESTED status.
     */
    @Test
    @DisplayName("Should create appointment with REQUESTED status")
    void shouldCreateAppointmentWithRequestedStatus() {
        User provider = createUser("provider", "p@test.com", UserRole.PROVIDER);
        User client = createUser("client", "c@test.com", UserRole.CLIENT);

        Offering offering = createOffering(provider);

        Appointment appointment = new Appointment(
                provider,
                client,
                offering,
                LocalDateTime.of(2026, 5, 1, 10, 0)
        );

        appointmentRepository.save(appointment);

        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.REQUESTED);
    }

    /**
     * Verifies status transitions.
     */
    @Test
    @DisplayName("Should change appointment status correctly")
    void shouldChangeAppointmentStatusCorrectly() {
        User provider = createUser("provider2", "p2@test.com", UserRole.PROVIDER);
        User client = createUser("client2", "c2@test.com", UserRole.CLIENT);

        Offering offering = createOffering(provider);

        Appointment appointment = new Appointment(
                provider,
                client,
                offering,
                LocalDateTime.now()
        );

        appointment.confirm();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);

        appointment.cancel();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.CANCELLED);

        appointment.complete();
        assertThat(appointment.getStatus()).isEqualTo(AppointmentStatus.COMPLETED);
    }

    /**
     * Verifies repository query by provider.
     */
    @Test
    @DisplayName("Should find appointments by provider")
    void shouldFindAppointmentsByProvider() {
        User provider = createUser("provider3", "p3@test.com", UserRole.PROVIDER);
        User client = createUser("client3", "c3@test.com", UserRole.CLIENT);

        Offering offering = createOffering(provider);

        Appointment a1 = new Appointment(provider, client, offering,
                LocalDateTime.of(2026, 5, 1, 10, 0));
        Appointment a2 = new Appointment(provider, client, offering,
                LocalDateTime.of(2026, 5, 2, 10, 0));

        appointmentRepository.save(a1);
        appointmentRepository.save(a2);

        List<Appointment> result =
                appointmentRepository.findAllByProviderOrderByStartDateTimeUtcAsc(provider);

        assertThat(result).hasSize(2);
    }

    /**
     * Verifies filtering by status and time range.
     */
    @Test
    @DisplayName("Should filter appointments by status and time range")
    void shouldFilterAppointmentsByStatusAndTimeRange() {
        User provider = createUser("provider4", "p4@test.com", UserRole.PROVIDER);
        User client = createUser("client4", "c4@test.com", UserRole.CLIENT);

        Offering offering = createOffering(provider);

        Appointment a1 = new Appointment(provider, client, offering,
                LocalDateTime.of(2026, 5, 1, 10, 0));

        Appointment a2 = new Appointment(provider, client, offering,
                LocalDateTime.of(2026, 5, 10, 10, 0));

        a2.confirm();

        appointmentRepository.save(a1);
        appointmentRepository.save(a2);

        List<Appointment> result =
                appointmentRepository.findAllByProviderAndStatusInAndStartDateTimeUtcBetween(
                        provider,
                        List.of(AppointmentStatus.CONFIRMED),
                        LocalDateTime.of(2026, 5, 1, 0, 0),
                        LocalDateTime.of(2026, 5, 31, 23, 59)
                );

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    // ===== helpers =====

    private User createUser(String username, String email, UserRole role) {
        User user = new User();
        user.setUsername(username);
        user.setEmail(email);
        user.setPassword("123");
        user.setRole(role);
        return userRepository.save(user);
    }

    private Offering createOffering(User provider) {
        Offering offering = new Offering(
                provider,
                "Test offering",
                "desc",
                BigDecimal.valueOf(100.0),
                60
        );

        return offeringRepository.save(offering);
    }
}