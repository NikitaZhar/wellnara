package life.wellnara.model;

/**
 * Status of a client appointment with provider.
 *
 * <p>Lifecycle: {@link #REQUESTED} → {@link #SCHEDULED} → {@link #COMPLETED},
 * with branches {@link #CANCELLED} (initiator and time recorded on the
 * appointment) and {@link #NO_SHOW}. A provider "reschedule" is a
 * provider-initiated {@link #CANCELLED} that frees the old slot; the client then
 * books a new time. Payment is no longer part of the model.
 */
public enum AppointmentStatus {

    /** Client requested an appointment; provider has not accepted it yet. */
    REQUESTED,

    /** Provider accepted the request; the appointment is booked. */
    SCHEDULED,

    /** Appointment was cancelled; see the cancellation initiator and time. */
    CANCELLED,

    /** Appointment took place. */
    COMPLETED,

    /** Client did not attend the scheduled appointment. */
    NO_SHOW
}
