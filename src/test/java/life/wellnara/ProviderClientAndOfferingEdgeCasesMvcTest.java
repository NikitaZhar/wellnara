package life.wellnara;

import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration MVC tests for edge cases in provider-client and offering flows.
 *
 * <p>These tests verify data integrity and access restrictions
 * that are easy to miss in the main happy-path scenarios.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProviderClientAndOfferingEdgeCasesMvcTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProviderClientLinkRepository providerClientLinkRepository;

	@Autowired
	private OfferingRepository offeringRepository;

	/**
	 * Verifies that admin can delete a client even when this client
	 * is linked to provider through provider_client_links.
	 *
	 * <p>Expected behavior:
	 * <ul>
	 *     <li>request redirects back to admin page,</li>
	 *     <li>client is removed from users table,</li>
	 *     <li>provider-client link is also removed.</li>
	 * </ul>
	 *
	 * @throws Exception if MockMvc request fails
	 */
	@Test
	@DisplayName("Should delete client by admin together with provider-client link")
	@DirtiesContext
	void shouldDeleteClientByAdminTogetherWithProviderClientLink() throws Exception {
		User admin = getAdminUser();
		User provider = createProvider("provider-admin-delete", "provider-admin-delete@example.com", "123");
		User client = createClient("client-admin-delete", "client-admin-delete@example.com", "123");

		ProviderClientLink link = new ProviderClientLink(provider, client, LocalDateTime.now());
		providerClientLinkRepository.save(link);

		MockHttpSession adminSession = createSessionWithCurrentUser(admin);

		mockMvc.perform(post("/admin/users/{id}/delete", client.getId())
				.session(adminSession))
		.andExpect(status().is3xxRedirection())
		.andExpect(redirectedUrl("/admin"));

		assertThat(userRepository.findById(client.getId())).isEmpty();
		assertThat(providerClientLinkRepository.findByProviderAndClientId(provider, client.getId())).isEmpty();
	}

	/**
	 * Verifies that provider cannot delete a client belonging to another provider.
	 *
	 * <p>Expected behavior:
	 * <ul>
	 *     <li>request fails because client does not belong to current provider,</li>
	 *     <li>client remains in database,</li>
	 *     <li>provider-client link remains in database.</li>
	 * </ul>
	 *
	 * @throws Exception if MockMvc request fails
	 */
	@Test
	@DisplayName("Should not allow provider to delete another provider client")
	void shouldNotAllowProviderToDeleteAnotherProviderClient() throws Exception {
		User providerOne = createProvider("provider-one-edge", "provider-one-edge@example.com", "123");
		User providerTwo = createProvider("provider-two-edge", "provider-two-edge@example.com", "123");
		User client = createClient("client-edge", "client-edge@example.com", "123");

		ProviderClientLink link = new ProviderClientLink(providerOne, client, LocalDateTime.now());
		providerClientLinkRepository.save(link);

		MockHttpSession providerTwoSession = createSessionWithCurrentUser(providerTwo);

		mockMvc.perform(post("/provider/clients/{clientId}/delete", client.getId())
				.session(providerTwoSession))
				.andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/provider"));

		assertThat(userRepository.findById(client.getId())).isPresent();
		assertThat(providerClientLinkRepository.findByProviderAndClientId(providerOne, client.getId())).isPresent();
	}

	/**
	 * Verifies that created offering is saved for current provider only.
	 *
	 * @throws Exception if MockMvc request fails
	 */
	@Test
	@DisplayName("Should save offering for current provider")
	void shouldSaveOfferingForCurrentProvider() throws Exception {
		User provider = createProvider("provider-offering-save", "provider-offering-save@example.com", "123");
		MockHttpSession providerSession = createSessionWithCurrentUser(provider);

		mockMvc.perform(post("/provider/offerings")
				.session(providerSession)
				.param("name", "Consultation")
				.param("description", "First consultation")
				.param("pricePerSession", "45.00")
				.param("durationMinutes", "60"))
		.andExpect(status().is3xxRedirection())
		.andExpect(redirectedUrl("/provider"));

		List<Offering> offerings = offeringRepository.findAllByProvider(provider);

		assertThat(offerings).hasSize(1);
		assertThat(offerings.get(0).getName()).isEqualTo("Consultation");
		assertThat(offerings.get(0).getDescription()).isEqualTo("First consultation");
		assertThat(offerings.get(0).getPricePerSession()).isEqualByComparingTo(new BigDecimal("45.00"));
		assertThat(offerings.get(0).getDurationMinutes()).isEqualTo(60);
		assertThat(offerings.get(0).isActive()).isTrue();
		assertThat(offerings.get(0).getProvider().getId()).isEqualTo(provider.getId());
	}

	/**
	 * Verifies that provider page shows only offerings of current provider.
	 *
	 * @throws Exception if MockMvc request fails
	 */
	@Test
	@DisplayName("Should show only own offerings on provider page")
	void shouldShowOnlyOwnOfferingsOnProviderPage() throws Exception {
		User providerOne = createProvider("provider-own-offerings", "provider-own-offerings@example.com", "123");
		User providerTwo = createProvider("provider-other-offerings", "provider-other-offerings@example.com", "123");

		offeringRepository.save(new Offering(
				providerOne,
				"Own Offering",
				"Visible only to provider one",
				new BigDecimal("20.00"),
				30
				));

		offeringRepository.save(new Offering(
				providerTwo,
				"Foreign Offering",
				"Should not be visible to provider one",
				new BigDecimal("90.00"),
				90
				));

		MockHttpSession providerOneSession = createSessionWithCurrentUser(providerOne);

		mockMvc.perform(get("/provider").session(providerOneSession))
		.andExpect(status().isOk())
		.andExpect(content().string(containsString("Own Offering")))
		.andExpect(content().string(not(containsString("Foreign Offering"))));
	}

	/**
	 * Returns preloaded admin user from test database.
	 *
	 * @return admin user
	 */
	private User getAdminUser() {
		return userRepository.findByUsername("admin")
				.orElseThrow(() -> new IllegalStateException("Admin user not found in test database"));
	}

	/**
	 * Creates provider user for test scenario.
	 *
	 * @param username provider username
	 * @param email provider email
	 * @param password provider password
	 * @return saved provider user
	 */
	private User createProvider(String username, String email, String password) {
		User provider = new User();
		provider.setUsername(username);
		provider.setPassword(password);
		provider.setEmail(email);
		provider.setRole(UserRole.PROVIDER);
		return userRepository.save(provider);
	}

	/**
	 * Creates client user for test scenario.
	 *
	 * @param username client username
	 * @param email client email
	 * @param password client password
	 * @return saved client user
	 */
	private User createClient(String username, String email, String password) {
		User client = new User();
		client.setUsername(username);
		client.setPassword(password);
		client.setEmail(email);
		client.setRole(UserRole.CLIENT);
		return userRepository.save(client);
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