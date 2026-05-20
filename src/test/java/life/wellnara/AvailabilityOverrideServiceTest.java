package life.wellnara;

import life.wellnara.model.AvailabilityOverride;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AvailabilityOverrideRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.AvailabilityOverrideService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for one-time availability override service.
 */
@SpringBootTest
@Transactional
class AvailabilityOverrideServiceTest {

    @Autowired
    private AvailabilityOverrideService availabilityOverrideService;

    @Autowired
    private AvailabilityOverrideRepository availabilityOverrideRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should create availability override")
    void shouldCreateAvailabilityOverride() {
        User provider = createProvider("provider-create-override");

        LocalDate date = LocalDate.now(ZoneId.of("Europe/Bratislava")).plusDays(1);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(12, 0);

        availabilityOverrideService.createAvailabilityOverride(
                provider,
                date,
                startTime,
                endTime,
                AvailabilityOverrideType.AVAILABLE
        );

        List<AvailabilityOverride> overrides =
                availabilityOverrideRepository.findAllByProviderOrderByOverrideDateAscStartTimeAsc(provider);

        assertThat(overrides).hasSize(1);

        AvailabilityOverride savedOverride = overrides.get(0);

        assertThat(savedOverride.getProvider().getId()).isEqualTo(provider.getId());
        assertThat(savedOverride.getOverrideDate()).isEqualTo(date);
        assertThat(savedOverride.getStartTime()).isEqualTo(startTime);
        assertThat(savedOverride.getEndTime()).isEqualTo(endTime);
        assertThat(savedOverride.getType()).isEqualTo(AvailabilityOverrideType.AVAILABLE);
    }

    @Test
    @DisplayName("Should delete own availability override")
    void shouldDeleteOwnAvailabilityOverride() {
        User provider = createProvider("provider-delete-override");

        AvailabilityOverride availabilityOverride = availabilityOverrideRepository.save(
                new AvailabilityOverride(
                        provider,
                        LocalDate.now(ZoneId.of("Europe/Bratislava")).plusDays(1),
                        LocalTime.of(9, 0),
                        LocalTime.of(11, 0),
                        AvailabilityOverrideType.UNAVAILABLE
                )
        );

        availabilityOverrideService.deleteAvailabilityOverride(
                provider,
                availabilityOverride.getId()
        );

        assertThat(availabilityOverrideRepository.findById(availabilityOverride.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Should not delete another provider availability override")
    void shouldNotDeleteAnotherProviderAvailabilityOverride() {
        User owner = createProvider("provider-owner-override");
        User anotherProvider = createProvider("provider-another-override");

        AvailabilityOverride availabilityOverride = availabilityOverrideRepository.save(
                new AvailabilityOverride(
                        owner,
                        LocalDate.now(ZoneId.of("Europe/Bratislava")).plusDays(1),
                        LocalTime.of(9, 0),
                        LocalTime.of(11, 0),
                        AvailabilityOverrideType.UNAVAILABLE
                )
        );

        assertThatThrownBy(() -> availabilityOverrideService.deleteAvailabilityOverride(
                anotherProvider,
                availabilityOverride.getId()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Availability override does not belong to provider");

        assertThat(availabilityOverrideRepository.findById(availabilityOverride.getId()))
                .isPresent();
    }

    @Test
    @DisplayName("Should return provider overrides ordered by date and start time")
    void shouldReturnProviderOverridesOrderedByDateAndStartTime() {
        User provider = createProvider("provider-list-override");

        LocalDate firstDate = LocalDate.now(ZoneId.of("Europe/Bratislava")).plusDays(1);
        LocalDate secondDate = firstDate.plusDays(1);

        AvailabilityOverride laterOnFirstDate = availabilityOverrideRepository.save(
                new AvailabilityOverride(
                        provider,
                        firstDate,
                        LocalTime.of(14, 0),
                        LocalTime.of(15, 0),
                        AvailabilityOverrideType.AVAILABLE
                )
        );

        AvailabilityOverride earlierOnFirstDate = availabilityOverrideRepository.save(
                new AvailabilityOverride(
                        provider,
                        firstDate,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        AvailabilityOverrideType.UNAVAILABLE
                )
        );

        AvailabilityOverride onSecondDate = availabilityOverrideRepository.save(
                new AvailabilityOverride(
                        provider,
                        secondDate,
                        LocalTime.of(8, 0),
                        LocalTime.of(9, 0),
                        AvailabilityOverrideType.AVAILABLE
                )
        );

        List<AvailabilityOverride> overrides =
                availabilityOverrideService.getAvailabilityOverrides(provider);

        assertThat(overrides)
                .extracting(AvailabilityOverride::getId)
                .containsExactly(
                        earlierOnFirstDate.getId(),
                        laterOnFirstDate.getId(),
                        onSecondDate.getId()
                );
    }

    private User createProvider(String usernamePrefix) {
        String username = usernamePrefix + "-" + System.nanoTime();

        User provider = new User();
        provider.setUsername(username);
        provider.setEmail(username + "@test.com");
        provider.setPassword("123");
        provider.setRole(UserRole.PROVIDER);

        return userRepository.save(provider);
    }
}