package life.wellnara.repository;

import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for provider availability rules.
 */
public interface AvailabilityRuleRepository extends JpaRepository<AvailabilityRule, Long> {

    List<AvailabilityRule> findAllByAvailabilityPeriod(AvailabilityPeriod availabilityPeriod);
}