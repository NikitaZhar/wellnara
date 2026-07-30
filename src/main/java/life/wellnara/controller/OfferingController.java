package life.wellnara.controller;

import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.service.OfferingService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * Controller for provider offering management.
 */
@Controller
public class OfferingController {

	private static final String PROVIDER_VIEW = "provider";

	private final OfferingService offeringService;
	private final ProviderPageModelAssembler providerPageModelAssembler;

	/**
	 * Creates offering controller.
	 *
	 * @param offeringService            service for offering operations
	 * @param providerPageModelAssembler assembler used to re-render the provider
	 *                                   page when an offering operation is rejected
	 */
	public OfferingController(OfferingService offeringService,
			ProviderPageModelAssembler providerPageModelAssembler) {
		this.offeringService = offeringService;
		this.providerPageModelAssembler = providerPageModelAssembler;
	}

	/**
	 * Creates a new offering for the current provider.
	 *
	 * <p>Domain rejections (e.g. the provider currency is not set) are shown as a
	 * message on the provider page instead of surfacing as a 500 error.
	 *
	 * @param name             offering name
	 * @param description      offering description
	 * @param pricePerSession  price per session in the provider currency
	 * @param durationMinutes  session duration in minutes
	 * @param currentUser      authenticated provider
	 * @param model            MVC model
	 * @return redirect to the offerings section, or the provider page with an error
	 */
	@PostMapping("/provider/offerings")
	public String createOffering(@RequestParam String name,
	                             @RequestParam String description,
	                             @RequestParam BigDecimal pricePerSession,
	                             @RequestParam Integer durationMinutes,
	                             @CurrentUser User currentUser,
	                             Model model) {
	    try {
	        offeringService.createOffering(
	                currentUser,
	                name,
	                description,
	                pricePerSession,
	                durationMinutes
	        );
	        return "redirect:/provider?section=offerings";
	    } catch (IllegalArgumentException exception) {
	        providerPageModelAssembler.populate(model, currentUser);
	        model.addAttribute("offeringError", exception.getMessage());
	        return PROVIDER_VIEW;
	    }
	}

	/**
	 * Shows the edit page for an offering owned by the current provider.
	 *
	 * @param offeringId  offering identifier
	 * @param currentUser authenticated provider
	 * @param model       MVC model
	 * @return offering edit view name
	 */
	@GetMapping("/provider/offerings/{offeringId}/edit")
	public String showEditPage(@PathVariable Long offeringId,
			@CurrentUser User currentUser,
			Model model) {
		Offering offering = offeringService.getOfferingOfProvider(currentUser, offeringId);
		model.addAttribute("offering", offering);
		return "offering-edit";
	}

	/**
	 * Updates an offering owned by the current provider.
	 *
	 * @param offeringId      offering identifier
	 * @param name            offering name
	 * @param description     offering description
	 * @param pricePerSession price per session in the provider currency
	 * @param durationMinutes session duration in minutes
	 * @param currentUser     authenticated provider
	 * @param model           MVC model
	 * @return redirect to the offerings section, or the edit page with an error
	 */
	@PostMapping("/provider/offerings/{offeringId}/edit")
	public String updateOffering(@PathVariable Long offeringId,
			@RequestParam String name,
			@RequestParam String description,
			@RequestParam BigDecimal pricePerSession,
			@RequestParam Integer durationMinutes,
			@CurrentUser User currentUser,
			Model model) {
		try {
			offeringService.updateOffering(
					currentUser,
					offeringId,
					name,
					description,
					pricePerSession,
					durationMinutes
					);
			return "redirect:/provider?section=offerings";
		} catch (IllegalArgumentException exception) {
			model.addAttribute("offering",
					offeringService.getOfferingOfProvider(currentUser, offeringId));
			model.addAttribute("offeringError", exception.getMessage());
			return "offering-edit";
		}
	}
}
