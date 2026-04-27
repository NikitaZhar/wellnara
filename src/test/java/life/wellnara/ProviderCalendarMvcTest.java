package life.wellnara;

import life.wellnara.model.AvailabilityPeriod;
import life.wellnara.model.AvailabilityRule;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AvailabilityPeriodRepository;
import life.wellnara.repository.AvailabilityRuleRepository;
import life.wellnara.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration MVC tests for provider calendar management.
 *
 * <p>These tests verify calendar validation, database persistence,
 * rollback-like behavior on invalid input and access protection.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderCalendarMvcTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AvailabilityPeriodRepository availabilityPeriodRepository;

    @Autowired
    private AvailabilityRuleRepository availabilityRuleRepository;

    /**
     * Verifies that a valid calendar form creates one availability period
     * and one rule for each filled weekday.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should save valid provider calendar into database")
    void shouldSaveValidProviderCalendarIntoDatabase() throws Exception {
        User provider = createProvider("calendar-provider-valid", "calendar-valid@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-01")
                        .param("planningTo", "2026-05-31")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("mondayStart", "09:00")
                        .param("mondayEnd", "13:00")
                        .param("wednesdayStart", "14:00")
                        .param("wednesdayEnd", "18:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider?section=calendar"));

        List<AvailabilityPeriod> periods = availabilityPeriodRepository.findAllByProvider(provider);

        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).getDateFrom()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(periods.get(0).getDateTo()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(periods.get(0).getProviderTimezone()).isEqualTo("Europe/Bratislava");

        List<AvailabilityRule> rules = availabilityRuleRepository.findAllByAvailabilityPeriod(periods.get(0));

        assertThat(rules).hasSize(2);
        assertThat(rules)
                .extracting(AvailabilityRule::getStartTime)
                .containsExactlyInAnyOrder(LocalTime.of(9, 0), LocalTime.of(14, 0));
    }

    /**
     * Verifies that 00:00-00:00 is treated as empty day
     * and does not create availability rule.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should treat midnight to midnight as empty weekday")
    void shouldTreatMidnightToMidnightAsEmptyWeekday() throws Exception {
        User provider = createProvider("calendar-provider-midnight", "calendar-midnight@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-01")
                        .param("planningTo", "2026-05-31")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("tuesdayStart", "00:00")
                        .param("tuesdayEnd", "00:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider?section=calendar"));

        List<AvailabilityPeriod> periods = availabilityPeriodRepository.findAllByProvider(provider);

        assertThat(periods).hasSize(1);
        assertThat(availabilityRuleRepository.findAllByAvailabilityPeriod(periods.get(0))).isEmpty();
    }

    /**
     * Verifies that equal non-midnight start and end time is rejected
     * and no database records are created.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should reject equal non-midnight time range")
    void shouldRejectEqualNonMidnightTimeRange() throws Exception {
        User provider = createProvider("calendar-provider-equal", "calendar-equal@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-01")
                        .param("planningTo", "2026-05-31")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("mondayStart", "12:00")
                        .param("mondayEnd", "12:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Monday end time must be after start time")));

        assertThat(availabilityPeriodRepository.findAllByProvider(provider)).isEmpty();
    }

    /**
     * Verifies that end time before start time is rejected
     * and no database records are created.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should reject end time before start time")
    void shouldRejectEndTimeBeforeStartTime() throws Exception {
        User provider = createProvider("calendar-provider-reversed", "calendar-reversed@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-01")
                        .param("planningTo", "2026-05-31")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("tuesdayStart", "15:00")
                        .param("tuesdayEnd", "11:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tuesday end time must be after start time")));

        assertThat(availabilityPeriodRepository.findAllByProvider(provider)).isEmpty();
    }

    /**
     * Verifies that start time without end time is rejected
     * and no database records are created.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should reject weekday with start time only")
    void shouldRejectWeekdayWithStartTimeOnly() throws Exception {
        User provider = createProvider("calendar-provider-start-only", "calendar-start-only@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-01")
                        .param("planningTo", "2026-05-31")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("wednesdayStart", "09:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wednesday must have both start and end time")));

        assertThat(availabilityPeriodRepository.findAllByProvider(provider)).isEmpty();
    }

    /**
     * Verifies that end time without start time is rejected
     * and no database records are created.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should reject weekday with end time only")
    void shouldRejectWeekdayWithEndTimeOnly() throws Exception {
        User provider = createProvider("calendar-provider-end-only", "calendar-end-only@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-01")
                        .param("planningTo", "2026-05-31")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("thursdayEnd", "13:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Thursday must have both start and end time")));

        assertThat(availabilityPeriodRepository.findAllByProvider(provider)).isEmpty();
    }

    /**
     * Verifies that missing planning start date is rejected.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should reject calendar without start date")
    void shouldRejectCalendarWithoutStartDate() throws Exception {
        User provider = createProvider("calendar-provider-no-start", "calendar-no-start@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningTo", "2026-05-31")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("mondayStart", "09:00")
                        .param("mondayEnd", "13:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Start date is required")));

        assertThat(availabilityPeriodRepository.findAllByProvider(provider)).isEmpty();
    }

    /**
     * Verifies that missing planning end date is rejected.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should reject calendar without end date")
    void shouldRejectCalendarWithoutEndDate() throws Exception {
        User provider = createProvider("calendar-provider-no-end", "calendar-no-end@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-01")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("mondayStart", "09:00")
                        .param("mondayEnd", "13:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("End date is required")));

        assertThat(availabilityPeriodRepository.findAllByProvider(provider)).isEmpty();
    }

    /**
     * Verifies that planning end date before start date is rejected.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should reject planning period when end date is before start date")
    void shouldRejectPlanningPeriodWhenEndDateIsBeforeStartDate() throws Exception {
        User provider = createProvider("calendar-provider-bad-period", "calendar-bad-period@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-31")
                        .param("planningTo", "2026-05-01")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("mondayStart", "09:00")
                        .param("mondayEnd", "13:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("End date must not be before start date")));

        assertThat(availabilityPeriodRepository.findAllByProvider(provider)).isEmpty();
    }

    /**
     * Verifies that missing timezone is rejected.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should reject calendar without provider timezone")
    void shouldRejectCalendarWithoutProviderTimezone() throws Exception {
        User provider = createProvider("calendar-provider-no-timezone", "calendar-no-timezone@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-01")
                        .param("planningTo", "2026-05-31")
                        .param("mondayStart", "09:00")
                        .param("mondayEnd", "13:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Timezone is required")));

        assertThat(availabilityPeriodRepository.findAllByProvider(provider)).isEmpty();
    }

    /**
     * Verifies that invalid calendar update does not overwrite previously saved valid calendar.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should keep previous valid calendar when new submission is invalid")
    void shouldKeepPreviousValidCalendarWhenNewSubmissionIsInvalid() throws Exception {
        User provider = createProvider("calendar-provider-keep-old", "calendar-keep-old@example.com");
        MockHttpSession session = createSessionWithCurrentUser(provider);

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-05-01")
                        .param("planningTo", "2026-05-31")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("mondayStart", "09:00")
                        .param("mondayEnd", "13:00"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/provider/calendar")
                        .session(session)
                        .param("planningFrom", "2026-06-01")
                        .param("planningTo", "2026-06-30")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("tuesdayStart", "15:00")
                        .param("tuesdayEnd", "11:00"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Tuesday end time must be after start time")));

        List<AvailabilityPeriod> periods = availabilityPeriodRepository.findAllByProvider(provider);

        assertThat(periods).hasSize(1);
        assertThat(periods.get(0).getDateFrom()).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(periods.get(0).getDateTo()).isEqualTo(LocalDate.of(2026, 5, 31));

        List<AvailabilityRule> rules = availabilityRuleRepository.findAllByAvailabilityPeriod(periods.get(0));

        assertThat(rules).hasSize(1);
        assertThat(rules.get(0).getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(rules.get(0).getEndTime()).isEqualTo(LocalTime.of(13, 0));
    }

    /**
     * Verifies that unauthenticated user cannot save calendar.
     *
     * @throws Exception if MockMvc request fails
     */
    @Test
    @DisplayName("Should redirect unauthenticated user to login when saving calendar")
    void shouldRedirectUnauthenticatedUserToLoginWhenSavingCalendar() throws Exception {
        mockMvc.perform(post("/provider/calendar")
                        .param("planningFrom", "2026-05-01")
                        .param("planningTo", "2026-05-31")
                        .param("providerTimezone", "Europe/Bratislava")
                        .param("mondayStart", "09:00")
                        .param("mondayEnd", "13:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/auth/login"));
    }

    /**
     * Creates provider user for test scenario.
     *
     * @param username provider username
     * @param email provider email
     * @return saved provider user
     */
    private User createProvider(String username, String email) {
        User provider = new User();
        provider.setUsername(username);
        provider.setEmail(email);
        provider.setPassword("123");
        provider.setRole(UserRole.PROVIDER);
        return userRepository.save(provider);
    }

    /**
     * Creates session containing authenticated current user.
     *
     * @param user authenticated user
     * @return mock HTTP session with currentUser attribute
     */
    private MockHttpSession createSessionWithCurrentUser(User user) {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("currentUser", user);
        return session;
    }
}