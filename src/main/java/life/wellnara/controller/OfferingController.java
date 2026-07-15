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
 * Controller for offering management.
 */
@Controller
public class OfferingController {

	private final OfferingService offeringService;

	/**
	 * Creates offering controller.
	 *
	 * @param offeringService service for offering management
	 */
	public OfferingController(OfferingService offeringService) {
		this.offeringService = offeringService;
	}

	/**
	 * Creates offering.
	 *
	 * @param name offering name
	 * @param description offering description
	 * @param pricePerSession price per session
	 * @param durationMinutes session duration in minutes
	 * @param currentUser authenticated provider
	 * @return redirect to provider page
	 */
	@PostMapping("/provider/offerings")
	public String createOffering(@RequestParam String name,
	                             @RequestParam String description,
	                             @RequestParam BigDecimal pricePerSession,
	                             @RequestParam Integer durationMinutes,
	                             @CurrentUser User currentUser) {
	    offeringService.createOffering(
	            currentUser,
	            name,
	            description,
	            pricePerSession,
	            durationMinutes
	    );

	    return "redirect:/provider?section=offerings";
	}

	/**
	 * Shows offering edit page.
	 *
	 * @param offeringId offering identifier
	 * @param currentUser authenticated provider
	 * @param model MVC model
	 * @return edit page
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
	 * Updates offering.
	 *
	 * @param offeringId offering identifier
	 * @param name offering name
	 * @param description offering description
	 * @param pricePerSession price per session
	 * @param durationMinutes session duration in minutes
	 * @param currentUser authenticated provider
	 * @return redirect to provider page
	 */
	@PostMapping("/provider/offerings/{offeringId}/edit")
	public String updateOffering(@PathVariable Long offeringId,
			@RequestParam String name,
			@RequestParam String description,
			@RequestParam BigDecimal pricePerSession,
			@RequestParam Integer durationMinutes,
			@CurrentUser User currentUser) {
		offeringService.updateOffering(
				currentUser,
				offeringId,
				name,
				description,
				pricePerSession,
				durationMinutes
				);

		return "redirect:/provider?section=offerings";
	}
}
