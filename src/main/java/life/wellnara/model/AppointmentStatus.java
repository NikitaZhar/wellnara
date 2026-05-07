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
     * Provider confirmed the termin and requested payment.
     */
    PAYMENT_REQUESTED,

    /**
     * Provider rejected the appointment request.
     */
    REJECTED,

    /**
     * Appointment was cancelled by provider.
     */
    CANCELLED_BY_PROVIDER,

    /**
     * Appointment was cancelled by client.
     */
    CANCELLED_BY_CLIENT,

    /**
     * Appointment was completed.
     */
    COMPLETED,

    /**
     * Client did not attend the appointment.
     */
    NO_SHOW
}