package life.wellnara.controller;

import life.wellnara.exception.LocalizedException;
import life.wellnara.model.User;
import life.wellnara.service.ClientPackageBookingService;
import life.wellnara.service.WalletCommandService;
import life.wellnara.web.CurrentUser;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Provider actions on client package requests: approve or decline. Thin
 * controller — rules and ownership checks live in the services. Both actions
 * return to the requests page where the request is listed.
 */
@Controller
public class ProviderPackageController {

    private static final String REQUESTS_REDIRECT = "redirect:/provider/requests";

    private final ClientPackageBookingService clientPackageBookingService;
    private final WalletCommandService walletCommandService;
    private final MessageSource messageSource;

    /**
     * Creates the provider package controller.
     *
     * @param clientPackageBookingService service approving a package with its first booking
     * @param walletCommandService        service for declining a package request
     * @param messageSource               resolver of localized user-facing messages
     */
    public ProviderPackageController(ClientPackageBookingService clientPackageBookingService,
                                     WalletCommandService walletCommandService,
                                     MessageSource messageSource) {
        this.clientPackageBookingService = clientPackageBookingService;
        this.walletCommandService = walletCommandService;
        this.messageSource = messageSource;
    }

    /**
     * Approves a package request: settles the held price, grants the sessions and
     * schedules the first session at the time the client chose. If that slot is no
     * longer free the whole approval is rolled back and the provider is told, so no
     * package is left approved without its first session booked.
     *
     * @param packageId          requested package identifier
     * @param currentUser        authenticated provider
     * @param redirectAttributes carrier for a one-off error message on failure
     * @return redirect to the requests page
     */
    @PostMapping("/provider/packages/{packageId}/accept")
    public String acceptPackage(@PathVariable Long packageId,
                                @CurrentUser User currentUser,
                                RedirectAttributes redirectAttributes) {
        try {
            clientPackageBookingService.acceptPackageRequest(currentUser, packageId);
        } catch (IllegalArgumentException notBookable) {
            // The chosen first-session slot is gone, or the request is not this
            // provider's — surface it so the provider can decline or ask to rebook.
            redirectAttributes.addFlashAttribute("packageError",
                    LocalizedException.resolve(notBookable, messageSource, LocaleContextHolder.getLocale()));
        } catch (IllegalStateException alreadyHandled) {
            // Already processed (e.g. double submit) — nothing to add.
        }
        return REQUESTS_REDIRECT;
    }

    /**
     * Declines a package request: releases the held price back to the client.
     *
     * @param packageId   requested package identifier
     * @param currentUser authenticated provider
     * @return redirect to the requests page
     */
    @PostMapping("/provider/packages/{packageId}/reject")
    public String rejectPackage(@PathVariable Long packageId, @CurrentUser User currentUser) {
        try {
            walletCommandService.rejectPackageRequest(currentUser, packageId);
        } catch (IllegalArgumentException | IllegalStateException alreadyHandled) {
            // Not found, not the provider's, or already processed (e.g. double submit).
        }
        return REQUESTS_REDIRECT;
    }
}
