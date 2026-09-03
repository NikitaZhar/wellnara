package life.wellnara.repository;

import life.wellnara.model.Appointment;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * Repository for provider-client appointments.
 */
public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    /**
     * Whether any appointment references the given offering, in any status.
     *
     * <p>Used to guard offering deletion: an offering referenced by an appointment
     * (past or present) cannot be deleted, since {@code offering_id} is a non-null
     * foreign key.
     *
     * @param offering offering to check
     * @return {@code true} if at least one appointment references it
     */
    boolean existsByOffering(Offering offering);

    /**
     * Whether the client has any appointment with the provider in one of the given
     * statuses. Used to block deactivating a client who still has open commitments
     * (e.g. {@code REQUESTED} or {@code SCHEDULED}).
     *
     * @param client   the client
     * @param provider the provider
     * @param statuses statuses to match
     * @return {@code true} if at least one matching appointment exists
     */
    boolean existsByClientAndProviderAndStatusIn(User client, User provider, Collection<AppointmentStatus> statuses);

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
    
    List<Appointment> findAllByProviderAndStatusInOrderByStartDateTimeUtcAsc(
            User provider,
            List<AppointmentStatus> statuses
    );
    
    void deleteAllByStatusInAndStartDateTimeUtcBefore(
            List<AppointmentStatus> statuses,
            LocalDateTime dateTimeUtc
    );
}