package life.wellnara;

import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static life.wellnara.SecurityTestSupport.authenticatedSession;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Route-level access tests for provider wallet actions: providers may act,
 * clients are forbidden by the {@code /provider/**} security rule.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class WalletAccessMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProviderClientLinkRepository providerClientLinkRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletEntryRepository walletEntryRepository;

    @Test
    @DisplayName("Provider can top up a linked client's wallet")
    void providerCanTopUp() throws Exception {
        User provider = provider("prov-mvc-ok");
        User client = linkedClient(provider, "client-mvc-ok");

        mockMvc.perform(post("/provider/clients/{clientId}/wallet/top-up", client.getId()).with(csrf())
                        .session(authenticatedSession(provider))
                        .param("amount", "75.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/provider/clients/" + client.getId() + "/wallet"));

        Wallet wallet = walletRepository.findByClient(client).orElseThrow();
        assertThat(walletEntryRepository.findAllByWalletOrderByIdAsc(wallet)).hasSize(1);
    }

    @Test
    @DisplayName("Client cannot use the provider top-up route and is redirected home")
    void clientForbiddenFromTopUp() throws Exception {
        User provider = provider("prov-mvc-deny");
        User client = linkedClient(provider, "client-mvc-deny");

        mockMvc.perform(post("/provider/clients/{clientId}/wallet/top-up", client.getId()).with(csrf())
                        .session(authenticatedSession(client))
                        .param("amount", "10.00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/home"));

        assertThat(walletRepository.findByClient(client)).isEmpty();
    }

    // ===== helpers =====

    private User provider(String username) {
        String unique = username + "-" + System.nanoTime();
        User user = new User();
        user.setUsername(unique);
        user.setEmail(unique + "@test.com");
        user.setPassword("123");
        user.setRole(UserRole.PROVIDER);
        user.setCurrency("EUR");
        return userRepository.save(user);
    }

    private User linkedClient(User provider, String username) {
        String unique = username + "-" + System.nanoTime();
        User client = new User();
        client.setUsername(unique);
        client.setEmail(unique + "@test.com");
        client.setPassword("123");
        client.setRole(UserRole.CLIENT);
        User saved = userRepository.save(client);
        providerClientLinkRepository.save(new ProviderClientLink(provider, saved, LocalDateTime.now()));
        return saved;
    }
}
