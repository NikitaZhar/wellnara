package life.wellnara;

import life.wellnara.dto.PackagePricing;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.OfferingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Validation of the optional per-offering package pricing.
 */
@SpringBootTest
@Transactional
class OfferingPackagePricingTest {

    @Autowired
    private OfferingService offeringService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("A valid package price marks the offering packageable")
    void validPackagePricingIsApplied() {
        User provider = provider();

        offeringService.createOffering(provider, "Massage", "desc",
                new BigDecimal("50.00"), 60, 0, 0,
                new PackagePricing(new BigDecimal("40.00"), 3, 10));

        Offering offering = onlyOffering(provider);
        assertThat(offering.isPackageable()).isTrue();
        assertThat(offering.getPackagePricePerSession()).isEqualByComparingTo("40.00");
        assertThat(offering.effectiveMinPackageSessions()).isEqualTo(3);
        assertThat(offering.effectiveMaxPackageSessions()).isEqualTo(10);
    }

    @Test
    @DisplayName("Empty package pricing leaves the offering not packageable")
    void emptyPackagePricingLeavesNonPackageable() {
        User provider = provider();

        offeringService.createOffering(provider, "Consultation", "desc",
                new BigDecimal("50.00"), 60, 0, 0, PackagePricing.none());

        assertThat(onlyOffering(provider).isPackageable()).isFalse();
    }

    @Test
    @DisplayName("Package per-session price cannot exceed the single-session price")
    void packagePriceCannotExceedSingle() {
        User provider = provider();

        assertThatThrownBy(() -> offeringService.createOffering(provider, "Massage", "desc",
                new BigDecimal("50.00"), 60, 0, 0,
                new PackagePricing(new BigDecimal("60.00"), null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exceed the single-session price");
    }

    @Test
    @DisplayName("Minimum package sessions cannot exceed the maximum")
    void minCannotExceedMax() {
        User provider = provider();

        assertThatThrownBy(() -> offeringService.createOffering(provider, "Massage", "desc",
                new BigDecimal("50.00"), 60, 0, 0,
                new PackagePricing(new BigDecimal("40.00"), 8, 4)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed the maximum");
    }

    @Test
    @DisplayName("Maximum package sessions cannot exceed the cap")
    void maxCannotExceedCap() {
        User provider = provider();

        assertThatThrownBy(() -> offeringService.createOffering(provider, "Massage", "desc",
                new BigDecimal("50.00"), 60, 0, 0,
                new PackagePricing(new BigDecimal("40.00"), 1, Offering.PACKAGE_SESSIONS_CAP + 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed");
    }

    private User provider() {
        String username = "prov-pkg-" + System.nanoTime();
        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(UserRole.PROVIDER);
        user.setCurrency("EUR");
        return userRepository.save(user);
    }

    private Offering onlyOffering(User provider) {
        List<Offering> offerings = offeringService.getOfferingsOfProvider(provider);
        assertThat(offerings).hasSize(1);
        return offerings.get(0);
    }
}
