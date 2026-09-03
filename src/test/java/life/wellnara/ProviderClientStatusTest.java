package life.wellnara;

import life.wellnara.exception.LocalizedException;
import life.wellnara.model.Appointment;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.ProviderClientService;
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
 * Integration tests for activating/deactivating a client and the open-commitment
 * guard that blocks deactivation.
 */
@SpringBootTest
@Transactional
class ProviderClientStatusTest {

    @Autowired
    private ProviderClientService providerClientService;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Autowired
    private AppointmentRepository appointmentRepository;

    @Autowired
    private OfferingRepository offeringRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("A client with no open commitments can be deactivated and reactivated")
    void deactivatesAndReactivates() {
        User provider = createUser("status-provider", UserRole.PROVIDER);
        User client = createUser("status-client", UserRole.CLIENT);
        link(provider, client);

        assertThat(providerClientService.isDeactivatable(provider, client)).isTrue();

        providerClientService.setClientActive(provider, client.getId(), false);
        assertThat(reloadLink(provider, client).isActive()).isFalse();

        providerClientService.setClientActive(provider, client.getId(), true);
        assertThat(reloadLink(provider, client).isActive()).isTrue();
    }

    @Test
    @DisplayName("A client with an open appointment cannot be deactivated")
    void refusesDeactivationWithOpenAppointment() {
        User provider = createUser("status-provider-open", UserRole.PROVIDER);
        User client = createUser("status-client-open", UserRole.CLIENT);
        link(provider, client);
        Offering offering = createOffering(provider);
        appointmentRepository.save(new Appointment(
                provider, client, offering, LocalDateTime.now().plusDays(1))); // REQUESTED (open)

        assertThat(providerClientService.isDeactivatable(provider, client)).isFalse();
        assertThatThrownBy(() -> providerClientService.setClientActive(provider, client.getId(), false))
                .isInstanceOf(LocalizedException.class);
        assertThat(reloadLink(provider, client).isActive()).isTrue();
    }

    private ProviderClientLink reloadLink(User provider, User client) {
        return providerClientLinkRepository.findByProviderAndClientId(provider, client.getId()).orElseThrow();
    }

    private void link(User provider, User client) {
        providerClientLinkRepository.save(new ProviderClientLink(provider, client, LocalDateTime.now()));
    }

    private Offering createOffering(User provider) {
        Offering offering = new Offering(provider, "Consultation", "A session",
                new BigDecimal("45.00"), 60);
        offering.setCurrency("EUR");
        return offeringRepository.save(offering);
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
