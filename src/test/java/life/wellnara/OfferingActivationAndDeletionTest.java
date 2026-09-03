package life.wellnara;

import life.wellnara.exception.LocalizedException;
import life.wellnara.model.Appointment;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.OfferingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for offering activation/deactivation and the guarded delete.
 */
@SpringBootTest
@Transactional
class OfferingActivationAndDeletionTest {

    @Autowired
    private OfferingService offeringService;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Deactivating then activating flips the offering's active flag")
    void togglesActiveFlag() {
        User provider = createProvider("offer-toggle");
        Offering offering = createOffering(provider);

        offeringService.setOfferingActive(provider, offering.getId(), false);
        assertThat(reload(offering).isActive()).isFalse();

        offeringService.setOfferingActive(provider, offering.getId(), true);
        assertThat(reload(offering).isActive()).isTrue();
    }

    @Test
    @DisplayName("An unreferenced offering is deletable and can be deleted")
    void deletesUnreferencedOffering() {
        User provider = createProvider("offer-delete-ok");
        Offering offering = createOffering(provider);

        assertThat(offeringService.isDeletable(offering)).isTrue();

        offeringService.deleteOffering(provider, offering.getId());

        assertThat(offeringRepository.findById(offering.getId())).isEmpty();
    }

    @Test
    @DisplayName("An offering referenced by an appointment is not deletable and delete is rejected")
    void refusesToDeleteReferencedOffering() {
        User provider = createProvider("offer-delete-blocked");
        User client = createClient("offer-delete-client");
        Offering offering = createOffering(provider);
        appointmentRepository.save(new Appointment(
                provider, client, offering, LocalDateTime.now().plusDays(1)));

        assertThat(offeringService.isDeletable(offering)).isFalse();
        assertThatThrownBy(() -> offeringService.deleteOffering(provider, offering.getId()))
                .isInstanceOf(LocalizedException.class);
        assertThat(offeringRepository.findById(offering.getId())).isPresent();
    }

    private Offering reload(Offering offering) {
        return offeringRepository.findById(offering.getId()).orElseThrow();
    }

    private Offering createOffering(User provider) {
        Offering offering = new Offering(provider, "Consultation", "A session",
                new BigDecimal("45.00"), 60);
        offering.setCurrency("EUR");
        return offeringRepository.save(offering);
    }

    private User createProvider(String usernamePrefix) {
        return createUser(usernamePrefix, UserRole.PROVIDER);
    }

    private User createClient(String usernamePrefix) {
        return createUser(usernamePrefix, UserRole.CLIENT);
    }

    private User createUser(String usernamePrefix, UserRole role) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User user = new User();
        user.setUsername(username);
        user.setEmail(username + "@test.com");
        user.setPassword("123");
        user.setRole(role);

        return userRepository.save(user);
    }
}
