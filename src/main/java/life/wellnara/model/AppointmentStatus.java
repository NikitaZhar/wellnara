package life.wellnara.model;

/**
 * Status of a client appointment with provider.
 */
public enum AppointmentStatus {

    /**
     * Client requested an appointment, but provider has not confirmed it yet.
     */
    REQUESTED,

    /**
     * Provider confirmed the appointment.
     */
    CONFIRMED,

    /**
     * Provider rejected the appointment request.
     */
    REJECTED,

    /**
     * Appointment was cancelled.
     */
    CANCELLED,

    /**
     * Appointment was completed.
     */
    COMPLETED,

    /**
     * Client did not attend the appointment.
     */
    NO_SHOW
}