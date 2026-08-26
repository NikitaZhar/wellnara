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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * MVC tests for wallet read surfaces: the provider per-client wallet page, the
 * role guard on it, and the client's own Home wallet tile.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WalletViewMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Autowired
    private WalletCommandService walletCommandService;

    @Test
    @DisplayName("Provider sees a linked client's balance and history on the wallet page")
    void providerSeesClientWalletPage() throws Exception {
        User provider = provider("prov-wallet-view");
        User client = linkedClient(provider, "client-wallet-view");
        walletCommandService.topUp(provider, client.getId(), new BigDecimal("100.00"), "cash");

        mockMvc.perform(get("/provider/clients/{clientId}/wallet", client.getId())
                        .session(authenticatedSession(provider)))
                .andExpect(status().isOk())
                .andExpect(view().name("provider-client-wallet"))
                .andExpect(content().string(containsString("История платежей")))
                // Amounts follow the active locale; the default is Russian, which uses a comma decimal separator.
                .andExpect(content().string(containsString("100,00")));
    }

    @Test
    @DisplayName("Client cannot open the provider wallet page and is redirected home")
    void clientForbiddenFromWalletPage() throws Exception {
        User provider = provider("prov-wallet-deny");
        User client = linkedClient(provider, "client-wallet-deny");

        mockMvc.perform(get("/provider/clients/{clientId}/wallet", client.getId())
                        .session(authenticatedSession(client)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));
    }

    @Test
    @DisplayName("Opening the wallet of a client not linked to the provider redirects to the clients list")
    void unlinkedClientRedirects() throws Exception {
        User owner = provider("prov-wallet-owner");
        User stranger = provider("prov-wallet-stranger");
        User client = linkedClient(owner, "client-wallet-owned");

        mockMvc.perform(get("/provider/clients/{clientId}/wallet", client.getId())
                        .session(authenticatedSession(stranger)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/clients"));
    }

    @Test
    @DisplayName("Client Home wallet tile shows the real available balance")
    void clientHomeShowsWalletBalance() throws Exception {
        User provider = provider("prov-home-wallet");
        User client = linkedClient(provider, "client-home-wallet");
        walletCommandService.topUp(provider, client.getId(), new BigDecimal("100.00"), null);

        mockMvc.perform(get("/home").session(authenticatedSession(client)))
                .andExpect(status().isOk())
                .andExpect(view().name("client-home"))
                // Amounts follow the active locale; the default is Russian, which uses a comma decimal separator.
                .andExpect(content().string(containsString("100,00")));
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
