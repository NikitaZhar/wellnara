package life.wellnara.controller;

import life.wellnara.model.User;
import life.wellnara.service.WalletCommandService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Provider actions on client package requests: approve or decline. Thin
 * controller — rules and ownership checks live in {@link WalletCommandService}.
 * Both actions return to the requests page where the request is listed.
 */
@Controller
public class ProviderPackageController {

    private static final String REQUESTS_REDIRECT = "redirect:/provider/requests";

    private final WalletCommandService walletCommandService;

    /**
     * Creates the provider package controller.
     *
     * @param walletCommandService service for package request approval
     */
    public ProviderPackageController(WalletCommandService walletCommandService) {
        this.walletCommandService = walletCommandService;
    }

    /**
     * Approves a package request: settles the held price and grants the sessions.
     *
     * @param packageId   requested package identifier
     * @param currentUser authenticated provider
     * @return redirect to the requests page
     */
    @PostMapping("/provider/packages/{packageId}/accept")
    public String acceptPackage(@PathVariable Long packageId, @CurrentUser User currentUser) {
        try {
            walletCommandService.acceptPackageRequest(currentUser, packageId);
        } catch (IllegalArgumentException | IllegalStateException alreadyHandled) {
            // Not found, not the provider's, or already processed (e.g. double submit).
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
