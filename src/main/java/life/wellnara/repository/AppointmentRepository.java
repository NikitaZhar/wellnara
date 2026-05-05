package life.wellnara.repository;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Repository for provider-client appointments.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    List<Appointment> findAllByProviderOrderByStartDateTimeUtcAsc(User provider);

    List<Appointment> findAllByClientOrderByStartDateTimeUtcAsc(User client);

    List<Appointment> findAllByProviderAndStatusInAndStartDateTimeUtcBetween(
            User provider,
            Collection<AppointmentStatus> statuses,
            LocalDateTime from,
            LocalDateTime to
    );
    
    List<Appointment> findAllByProviderAndStatusIn(
            User provider,
            Collection<AppointmentStatus> statuses
    );
    
    List<Appointment> findAllByProviderIdAndStatusIn(
            Long providerId,
            List<AppointmentStatus> statuses
    );
    
    List<Appointment> findAllByProviderAndStatusOrderByStartDateTimeUtcAsc(
            User provider,
            AppointmentStatus status
    );
    
    List<Appointment> findAllByStatusInAndStartDateTimeUtcBefore(
            Collection<AppointmentStatus> statuses,
            LocalDateTime before
    );
    
    List<Appointment> findAllByClientAndStatusOrderByStartDateTimeUtcAsc(
            User client,
            AppointmentStatus status
    );
    
    List<Appointment> findAllByClientAndStatusInOrderByStartDateTimeUtcAsc(
            User client,
            Collection<AppointmentStatus> statuses
    );
}