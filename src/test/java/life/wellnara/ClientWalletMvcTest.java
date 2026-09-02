package life.wellnara;

import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.service.WalletCommandService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static life.wellnara.SecurityTestSupport.authenticatedSession;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MVC tests for the client's own wallet page: it renders the balance and the
 * movement history, and shows an empty state for a client with no wallet yet.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ClientWalletMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Autowired
    private WalletCommandService walletCommandService;

    @Test
    @DisplayName("Client sees the balance and a movement on their wallet page")
    void clientSeesBalanceAndMovement() throws Exception {
        User provider = provider("prov-cwallet");
        User client = linkedClient(provider, "client-cwallet");
        walletCommandService.topUp(provider, client.getId(), new BigDecimal("100.00"), null);

        mockMvc.perform(get("/client/wallet").session(authenticatedSession(client)))
                .andExpect(status().isOk())
                .andExpect(view().name("client-wallet"))
                // Default locale is Russian: comma decimal separator and the top-up label.
                .andExpect(content().string(containsString("100,00")))
                .andExpect(content().string(containsString("Пополнение")))
                // A top-up is a money movement — its filter marker must render as a plain "true"/"false" string.
                .andExpect(content().string(containsString("data-money=\"true\"")));
    }

    @Test
    @DisplayName("A client with no wallet sees the empty movements state")
    void clientWithoutWalletSeesEmptyState() throws Exception {
        User provider = provider("prov-cwallet-empty");
        User client = linkedClient(provider, "client-cwallet-empty");

        mockMvc.perform(get("/client/wallet").session(authenticatedSession(client)))
                .andExpect(status().isOk())
                .andExpect(view().name("client-wallet"))
                .andExpect(content().string(containsString("Пока нет движений")));
    }

    // ===== helpers =====

    private User provider(String username) {
        User user = newUser(username, UserRole.PROVIDER);
        user.setCurrency("EUR");
        return userRepository.save(user);
    }

    private User linkedClient(User provider, String username) {
        User client = userRepository.save(newUser(username, UserRole.CLIENT));
        providerClientLinkRepository.save(new ProviderClientLink(provider, client, LocalDateTime.now()));
        return client;
    }

    private User newUser(String username, UserRole role) {
        String unique = username + "-" + System.nanoTime();
        User user = new User();
        user.setUsername(unique);
        user.setEmail(unique + "@test.com");
        user.setPassword("123");
        user.setRole(role);
        return user;
    }
}
