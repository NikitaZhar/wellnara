package life.wellnara.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Appointment request or confirmed meeting between client and provider.
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

    public void confirm() {
        this.status = AppointmentStatus.CONFIRMED;
        this.updatedAt = LocalDateTime.now();
    }

    public void reject() {
        this.status = AppointmentStatus.REJECTED;
        this.updatedAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = AppointmentStatus.CANCELLED;
        this.updatedAt = LocalDateTime.now();
    }

    public void complete() {
        this.status = AppointmentStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
    }

    public void markNoShow() {
        this.status = AppointmentStatus.NO_SHOW;
        this.updatedAt = LocalDateTime.now();
    }
}