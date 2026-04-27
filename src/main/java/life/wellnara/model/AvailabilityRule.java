package life.wellnara.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Weekly availability rule inside provider planning period.
 */
@Entity
@Table(name = "availability_rules")
public class AvailabilityRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "availability_period_id", nullable = false)
    private AvailabilityPeriod availabilityPeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AvailabilityDay dayOfWeek;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected AvailabilityRule() {
    }

    public AvailabilityRule(AvailabilityPeriod availabilityPeriod,
                            AvailabilityDay dayOfWeek,
                            LocalTime startTime,
                            LocalTime endTime) {
        this.availabilityPeriod = availabilityPeriod;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.createdAt = LocalDateTime.now();
    }
    
    public AvailabilityDay getDayOfWeek() {
        return dayOfWeek;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }
}