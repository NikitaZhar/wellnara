package life.wellnara.event;

/**
 * Published when a previously booked appointment is cancelled
 * ({@code SCHEDULED → CANCELLED}), including a client reschedule which cancels
 * the current booking before a new one is placed.
 *
 * <p>Rejections of a still-pending request ({@code REQUESTED → CANCELLED}) do
 * not publish this event: no calendar event ever existed for them.
 *
 * <p>Carries only the appointment identifier: listeners run after the
 * transaction commits and re-load the appointment in their own read-only
 * transaction, so no detached entity crosses the transaction boundary.
 *
 * @param appointmentId identifier of the cancelled appointment
 */
public record AppointmentCancelledEvent(long appointmentId) {
}
