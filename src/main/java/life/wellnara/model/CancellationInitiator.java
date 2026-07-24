package life.wellnara.model;

/**
 * Who initiated an appointment cancellation.
 *
 * <p>Recorded on a {@link AppointmentStatus#CANCELLED} appointment. The financial
 * settlement rules (phase 3.6) branch on this: a provider cancellation releases
 * the hold, while a late client cancellation may settle it.
 */
public enum CancellationInitiator {

    /** Provider cancelled (includes rejecting a request and rescheduling). */
    PROVIDER,

    /** Client cancelled. */
    CLIENT
}
