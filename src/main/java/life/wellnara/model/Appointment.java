package life.wellnara.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Appointment request or booked meeting between client and provider.
 */
@Entity
@Table(name = "appointments")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Provider who owns the appointment.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    /**
     * Client who requested the appointment.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    /**
     * Offering selected for this appointment.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offering_id", nullable = false)
    private Offering offering;

    /**
     * Appointment start date and time in UTC.
     */
    @Column(nullable = false)
    private LocalDateTime startDateTimeUtc;

    /**
     * Current appointment status.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentStatus status;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /**
     * Message shown to the client when the appointment is cancelled (rejection or
     * reschedule reason). {@code null} when no message was given.
     */
    @Column(length = 1000)
    private String rejectionReason;

    /**
     * Who initiated the cancellation; {@code null} until the appointment is cancelled.
     */
    @Enumerated(EnumType.STRING)
    @Column
    private CancellationInitiator cancellationInitiator;

    /**
     * When the appointment was cancelled, in UTC; {@code null} until cancelled.
     */
    @Column
    private LocalDateTime cancelledAt;

    /**
     * Whether the notification for this appointment has been dismissed. Once an
     * appointment carries wallet ledger entries it is never deleted; acknowledging
     * only hides it from the notification lists while keeping it in history.
     */
    @Column(nullable = false)
    private boolean acknowledged;

    /**
     * Optimistic-lock version. Two concurrent terminal transitions on the same
     * appointment then conflict, so exactly one settlement/release wins.
     */
    @Version
    @Column(nullable = false)
    private Long version;

    protected Appointment() {
    }

    public Appointment(User provider,
                       User client,
                       Offering offering,
                       LocalDateTime startDateTimeUtc) {
        this.provider = provider;
        this.client = client;
        this.offering = offering;
        this.startDateTimeUtc = startDateTimeUtc;
        this.status = AppointmentStatus.REQUESTED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getProvider() {
        return provider;
    }

    public User getClient() {
        return client;
    }

    public Offering getOffering() {
        return offering;
    }

    public LocalDateTime getStartDateTimeUtc() {
        return startDateTimeUtc;
    }

    public AppointmentStatus getStatus() {
        return status;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public CancellationInitiator getCancellationInitiator() {
        return cancellationInitiator;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public boolean isAcknowledged() {
        return acknowledged;
    }

    public Long getVersion() {
        return version;
    }

    /**
     * Accepts a requested appointment: {@code REQUESTED → SCHEDULED}.
     */
    public void schedule() {
        this.status = AppointmentStatus.SCHEDULED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Cancels the appointment, recording who cancelled and when.
     *
     * <p>Covers provider rejection, provider reschedule, and both provider and
     * client cancellations of a scheduled appointment.
     *
     * @param initiator who cancelled (required)
     * @param message   message shown to the client, or {@code null}/blank for none
     * @param at        cancellation time in UTC (supplied by the time service)
     */
    public void cancel(CancellationInitiator initiator, String message, LocalDateTime at) {
        Objects.requireNonNull(initiator, "Cancellation initiator is required");
        this.status = AppointmentStatus.CANCELLED;
        this.cancellationInitiator = initiator;
        this.cancelledAt = at;
        this.rejectionReason = (message == null || message.isBlank()) ? null : message.trim();
        this.updatedAt = at;
    }

    /**
     * Marks a scheduled appointment as completed: {@code SCHEDULED → COMPLETED}.
     */
    public void complete() {
        this.status = AppointmentStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Marks a scheduled appointment as a no-show: {@code SCHEDULED → NO_SHOW}.
     */
    public void markNoShow() {
        this.status = AppointmentStatus.NO_SHOW;
        this.updatedAt = LocalDateTime.now();
    }

    /**
     * Dismisses the notification for this appointment without deleting it.
     */
    public void acknowledge() {
        this.acknowledged = true;
        this.updatedAt = LocalDateTime.now();
    }
}
