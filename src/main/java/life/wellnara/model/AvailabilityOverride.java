package life.wellnara.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * One-time provider availability change for a specific date and time interval.
 */
@Entity
@Table(name = "availability_overrides")
public class AvailabilityOverride {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false)
    private LocalDate overrideDate;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvailabilityOverrideType type;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AvailabilityOverride() {
    }

    public AvailabilityOverride(User provider,
                                LocalDate overrideDate,
                                LocalTime startTime,
                                LocalTime endTime,
                                AvailabilityOverrideType type) {
        this.provider = provider;
        this.overrideDate = overrideDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.type = type;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public User getProvider() {
        return provider;
    }

    public LocalDate getOverrideDate() {
        return overrideDate;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public AvailabilityOverrideType getType() {
        return type;
    }
}