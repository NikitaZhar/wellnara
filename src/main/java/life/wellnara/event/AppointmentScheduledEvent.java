package life.wellnara.event;

/**
 * Published when an appointment becomes booked ({@code REQUESTED → SCHEDULED}).
 *
 * <p>Carries only the appointment identifier: listeners run after the
 * transaction commits and re-load the appointment in their own read-only
 * transaction, so no detached entity crosses the transaction boundary.
 *
 * @param appointmentId identifier of the scheduled appointment
 */
public record AppointmentScheduledEvent(long appointmentId) {
}
