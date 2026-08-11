package life.wellnara.service;

/**
 * Time thresholds that govern client-initiated changes to a scheduled appointment.
 *
 * <p>Kept in one place so the enforcement (in {@link AppointmentCommandService}) and
 * the UI action windows (in {@link AppointmentQueryService}) never drift apart.
 */
public final class AppointmentPolicy {

    /**
     * A client cancellation at or beyond this many hours before the start refunds the
     * hold; a later cancellation forfeits it (the service is treated as delivered).
     */
    public static final int CANCEL_REFUND_THRESHOLD_HOURS = 24;

    /**
     * A client may reschedule only at or beyond this many hours before the start.
     * A reschedule always keeps the payment (the hold is released so it can back the
     * new booking), so it is closed earlier than a cancellation to avoid last-minute
     * free moves.
     */
    public static final int RESCHEDULE_MIN_HOURS = 12;

    private AppointmentPolicy() {
    }
}
