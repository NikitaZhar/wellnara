package life.wellnara.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Offering created and owned by provider.
 */
@Entity
@Table(name = "offerings")
public class Offering {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private User provider;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal pricePerSession;

    @Column(nullable = false)
    private Integer durationMinutes;

    @Column(nullable = false)
    private boolean active;

    /**
     * Required by JPA.
     */
    public Offering() {
    }

    /**
     * Creates offering.
     *
     * @param provider provider owner
     * @param name offering name
     * @param description offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     */
    public Offering(User provider,
                    String name,
                    String description,
                    BigDecimal pricePerSession,
                    Integer durationMinutes) {
        this.provider = provider;
        this.name = name;
        this.description = description;
        this.pricePerSession = pricePerSession;
        this.durationMinutes = durationMinutes;
        this.active = true;
    }

    public Long getId() {
        return id;
    }

    public User getProvider() {
        return provider;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPricePerSession() {
        return pricePerSession;
    }

    public void setPricePerSession(BigDecimal pricePerSession) {
        this.pricePerSession = pricePerSession;
    }

    public Integer getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(Integer durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public boolean isActive() {
        return active;
    }
}