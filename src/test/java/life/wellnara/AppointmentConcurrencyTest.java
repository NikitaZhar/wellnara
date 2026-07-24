package life.wellnara;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import life.wellnara.model.Appointment;
import life.wellnara.model.CancellationInitiator;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests optimistic locking on appointments: two writers acting on the same
 * appointment from stale state conflict, so exactly one terminal transition wins.
 *
 * <p>This is the database's last word for "one final operation per appointment":
 * every terminal transition (complete, cancel, no-show) modifies the appointment,
 * so its {@code @Version} guards concurrent settlement/release. Staleness is
 * simulated deterministically by detaching a copy — no threads, no flakiness.
 *
 * <p>The partial unique indexes added in V5 (active slot, one final entry per
 * appointment) are the PostgreSQL backstop; they are not exercised here because
 * H2 does not support partial indexes.
 */
@SpringBootTest
@Transactional
class AppointmentConcurrencyTest {

    @PersistenceContext
    private EntityManager entityManager;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Test
    @DisplayName("Two concurrent terminal transitions on one appointment: exactly one succeeds")
    void concurrentTerminalTransitionsLoseOne() {
        Appointment scheduled = scheduledAppointment();
        Long id = scheduled.getId();

        // A stale copy held by a second writer that loaded before the first committed.
        entityManager.detach(scheduled);

        // First writer completes and commits: version 0 -> 1.
        Appointment fresh = appointmentRepository.findById(id).orElseThrow();
        fresh.complete();
        appointmentRepository.saveAndFlush(fresh);

        // Second writer acts on the stale copy (still version 0) -> conflict.
        scheduled.cancel(CancellationInitiator.PROVIDER, null, LocalDateTime.of(2026, 6, 1, 7, 0));

        // The stale writer loses; the winner (complete) already committed version 1.
        assertThatThrownBy(() -> appointmentRepository.saveAndFlush(scheduled))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    @DisplayName("Appointment version increments on each update")
    void versionIncrementsOnUpdate() {
        Appointment appointment = scheduledAppointment();
        Long initialVersion = appointment.getVersion();

        appointment.complete();
        appointmentRepository.saveAndFlush(appointment);

        assertThat(appointment.getVersion()).isGreaterThan(initialVersion);
    }

    // ===== helpers =====

    private Appointment scheduledAppointment() {
        User provider = user("prov-concurrency", UserRole.PROVIDER);
        User client = user("client-concurrency", UserRole.CLIENT);
        Offering offering = offeringRepository.save(
                new Offering(provider, "Consultation", "desc", new BigDecimal("100.00"), 60));

        Appointment appointment = new Appointment(
                provider, client, offering, LocalDateTime.of(2026, 6, 1, 8, 0));
        appointment.schedule();
        return appointmentRepository.saveAndFlush(appointment);
    }

    private User user(String prefix, UserRole role) {
        String username = prefix + "-" + System.nanoTime();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(role);
        return userRepository.save(user);
    }
}
