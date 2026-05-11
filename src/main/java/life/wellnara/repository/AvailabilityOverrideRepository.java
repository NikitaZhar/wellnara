package life.wellnara.repository;

import life.wellnara.model.AvailabilityOverride;
import life.wellnara.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for one-time provider availability overrides.
 */
public interface AvailabilityOverrideRepository extends JpaRepository<AvailabilityOverride, Long> {

    List<AvailabilityOverride> findAllByProviderOrderByOverrideDateAscStartTimeAsc(User provider);
}