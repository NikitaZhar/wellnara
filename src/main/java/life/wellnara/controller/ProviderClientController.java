package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.ProviderClientService;
import life.wellnara.service.SessionUserService;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Controller for provider client management.
 */
@Controller
public class ProviderClientController {

	private final ProviderClientService providerClientService;
	private final SessionUserService sessionUserService;

	/**
	 * Creates provider client controller.
	 *
	 * @param providerClientService service for provider client operations
	 * @param sessionUserService service for authenticated session user access
	 */
	public ProviderClientController(ProviderClientService providerClientService,
			SessionUserService sessionUserService) {
		this.providerClientService = providerClientService;
		this.sessionUserService = sessionUserService;
	}

	/**
	 * Deletes client of current provider.
	 *
	 * @param clientId client identifier
	 * @param session current HTTP session
	 * @return redirect to provider page or login page
	 */
	@PostMapping("/provider/clients/{clientId}/delete")
	public String deleteClient(@PathVariable Long clientId, HttpSession session) {
		User currentUser = sessionUserService.requireProvider(session);

		if (currentUser == null) {
		    return "redirect:/auth/login";
		}

		providerClientService.deleteClient(currentUser, clientId);

		return "redirect:/provider";
	}
}