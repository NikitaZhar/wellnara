package life.wellnara.controller;

import life.wellnara.model.User;
import life.wellnara.service.ProviderClientService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Controller for provider client management.
 */
@Controller
public class ProviderClientController {

	private final ProviderClientService providerClientService;

	/**
	 * Creates provider client controller.
	 *
	 * @param providerClientService service for provider client operations
	 */
	public ProviderClientController(ProviderClientService providerClientService) {
		this.providerClientService = providerClientService;
	}

	/**
	 * Deletes client of current provider.
	 *
	 * @param clientId client identifier
	 * @param currentUser authenticated provider
	 * @return redirect to provider page
	 */
	@PostMapping("/provider/clients/{clientId}/delete")
	public String deleteClient(@PathVariable Long clientId, @CurrentUser User currentUser) {
		providerClientService.deleteClient(currentUser, clientId);

		return "redirect:/provider";
	}
}
