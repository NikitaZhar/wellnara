package life.wellnara;

import life.wellnara.service.calendar.CalendarFeedService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the feed endpoint end-to-end at the web layer: the {@code {token}.ics}
 * path maps, the route is reachable without authentication ({@code permitAll}),
 * and a resolved feed is served as {@code text/calendar} while an unresolved one
 * is a {@code 404}. The feed body itself is built by {@link CalendarFeedService},
 * mocked here so this test stays focused on routing and security.
 */
@SpringBootTest
@AutoConfigureMockMvc
class CalendarFeedControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CalendarFeedService calendarFeedService;

    @Test
    @DisplayName("A resolved token is served as a text/calendar document without authentication")
    void servesFeedForResolvedToken() throws Exception {
        when(calendarFeedService.feedFor("feed-token")).thenReturn(Optional.of("BEGIN:VCALENDAR"));

        mockMvc.perform(get("/calendar/{token}.ics", "feed-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/calendar"))
                .andExpect(content().string("BEGIN:VCALENDAR"));
    }

    @Test
    @DisplayName("An unresolved token is a 404")
    void notFoundForUnresolvedToken() throws Exception {
        when(calendarFeedService.feedFor("unknown")).thenReturn(Optional.empty());

        mockMvc.perform(get("/calendar/{token}.ics", "unknown"))
                .andExpect(status().isNotFound());
    }
}
