package life.wellnara.controller;

import life.wellnara.exception.LocalizedException;
import life.wellnara.model.User;
import life.wellnara.service.UserProfileService;
import life.wellnara.service.WalletCommandService;
import life.wellnara.service.WalletQueryService;
import life.wellnara.web.CurrentUser;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * Provider actions on a client's wallet: view the wallet and top it up.
 *
 * <p>Thin controller — all rules and role checks live in the services. The
 * {@code /provider/**} security rule already restricts these routes to
 * providers; the services re-check ownership. The read page
 * ({@code GET .../wallet}) is served from {@link WalletQueryService}; the
 * top-up from {@link WalletCommandService}. On both success and failure the
 * top-up stays on the client's wallet page, so the provider sees the updated
 * balance (or the error) in place.
 */
@Controller
public class WalletController {

    private static final String WALLET_VIEW = "provider-client-wallet";
    private static final String CLIENTS_REDIRECT = "redirect:/provider/clients";

    private final WalletCommandService walletCommandService;
    private final WalletQueryService walletQueryService;
    private final UserProfileService userProfileService;
    private final MessageSource messageSource;

    /**
     * Creates wallet controller.
     *
     * @param walletCommandService service for provider wallet operations
     * @param walletQueryService   service for read-only wallet views
     * @param userProfileService   service resolving the provider display name
     * @param messageSource        resolver of localized user-facing messages
     */
    public WalletController(WalletCommandService walletCommandService,
                            WalletQueryService walletQueryService,
                            UserProfileService userProfileService,
                            MessageSource messageSource) {
        this.walletCommandService = walletCommandService;
        this.walletQueryService = walletQueryService;
        this.userProfileService = userProfileService;
        this.messageSource = messageSource;
    }

    /**
     * Treats a blank optional comment as {@code null} so it binds cleanly.
     *
     * @param binder the request data binder
     */
    @InitBinder
    void trimEmptyToNull(WebDataBinder binder) {
        binder.registerCustomEditor(String.class, new StringTrimmerEditor(true));
    }

    /**
     * Shows a client's wallet to the provider: balance, the full movement history
     * and the top-up form.
     *
     * @param clientId    client whose wallet is shown
     * @param currentUser authenticated provider
     * @param model       MVC model
     * @return the client wallet page, or a redirect to the clients list if the client is not linked
     */
    @GetMapping("/provider/clients/{clientId}/wallet")
    public String showClientWallet(@PathVariable Long clientId,
                                   @CurrentUser User currentUser,
                                   Model model) {
        try {
            populateWalletPage(model, currentUser, clientId);
            return WALLET_VIEW;
        } catch (IllegalArgumentException notLinked) {
            return CLIENTS_REDIRECT;
        }
    }

    /**
     * Records a manual wallet top-up for a client.
     *
     * @param clientId    client whose wallet is credited
     * @param amount      positive amount in the provider currency
     * @param comment     optional note
     * @param currentUser authenticated provider
     * @param model       MVC model
     * @return redirect back to the client's wallet page, or that page re-rendered with an error
     */
    @PostMapping("/provider/clients/{clientId}/wallet/top-up")
    public String topUp(@PathVariable Long clientId,
                        @RequestParam BigDecimal amount,
                        @RequestParam(required = false) String comment,
                        @CurrentUser User currentUser,
                        Model model) {
        return execute(currentUser, clientId, model,
                provider -> walletCommandService.topUp(provider, clientId, amount, comment));
    }

    /**
     * Runs a money-in action and keeps the provider on the client's wallet page:
     * a redirect on success (so the updated balance is fetched fresh), or the same
     * page re-rendered with the error. Falls back to the clients list only if the
     * client is not the provider's (the wallet page cannot be built).
     */
    private String execute(User currentUser, Long clientId, Model model, WalletAction action) {
        try {
            action.execute(currentUser);
            return "redirect:/provider/clients/" + clientId + "/wallet";
        } catch (IllegalArgumentException exception) {
            try {
                populateWalletPage(model, currentUser, clientId);
                model.addAttribute("walletActionError", LocalizedException.resolve(exception, messageSource, LocaleContextHolder.getLocale()));
                return WALLET_VIEW;
            } catch (IllegalArgumentException notLinked) {
                return CLIENTS_REDIRECT;
            }
        }
    }

    private void populateWalletPage(Model model, User provider, Long clientId) {
        model.addAttribute("wallet", walletQueryService.getWalletForProvider(provider, clientId));
        model.addAttribute("clientId", clientId);
        model.addAttribute("providerName", userProfileService.resolveDisplayName(provider));
    }

    /**
     * Provider wallet action callback.
     */
    @FunctionalInterface
    private interface WalletAction {

        /**
         * Executes the wallet action for the authenticated provider.
         *
         * @param provider authenticated provider
         */
        void execute(User provider);
    }
}
