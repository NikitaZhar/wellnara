package life.wellnara.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Planning period for provider availability.
 */
@Entity
@Table(name = "availability_periods")
public class AvailabilityPeriod {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false)
    private LocalDate dateFrom;

    @Column(nullable = false)
    private LocalDate dateTo;

    @Column(nullable = false)
    private String providerTimezone;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected AvailabilityPeriod() {
    }

    public AvailabilityPeriod(User provider, LocalDate dateFrom, LocalDate dateTo, String providerTimezone) {
        this.provider = provider;
        this.dateFrom = dateFrom;
        this.dateTo = dateTo;
        this.providerTimezone = providerTimezone;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }
    
    public User getProvider() {
        return provider;
    }

    public LocalDate getDateFrom() {
        return dateFrom;
    }

    public LocalDate getDateTo() {
        return dateTo;
    }

    public String getProviderTimezone() {
        return providerTimezone;
    }
}