package life.wellnara.repository;

import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for provider availability planning periods.
 */
public interface AvailabilityPeriodRepository extends JpaRepository<AvailabilityPeriod, Long> {

    List<AvailabilityPeriod> findAllByProvider(User provider);

    Optional<AvailabilityPeriod> findTopByProviderOrderByCreatedAtDesc(User provider);
}