package life.wellnara.service.email;

import life.wellnara.event.AppointmentCancelledEvent;
import life.wellnara.event.AppointmentScheduledEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Sends appointment notifications after the triggering transaction commits.
 *
 * <p>Listening on {@link TransactionPhase#AFTER_COMMIT} guarantees the email is
 * never sent for a change that later rolls back. The listener stays thin: it
 * only routes each event to {@link AppointmentNotificationService}, which owns
 * the loading and sending in its own transaction.
 */
@Component
public class AppointmentNotificationListener {

    private final AppointmentNotificationService notificationService;

    /**
     * Creates the appointment notification listener.
     *
     * @param notificationService service that builds and sends the notifications
     */
    public AppointmentNotificationListener(AppointmentNotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Sends the confirmation emails once a booking commits.
     *
     * @param event scheduled-appointment event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentScheduled(AppointmentScheduledEvent event) {
        notificationService.notifyScheduled(event.appointmentId());
    }

    /**
     * Sends the cancellation emails once a cancellation commits.
     *
     * @param event cancelled-appointment event
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        notificationService.notifyCancelled(event.appointmentId());
    }
}
