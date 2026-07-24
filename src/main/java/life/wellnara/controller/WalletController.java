package life.wellnara.controller;

import life.wellnara.model.User;
import life.wellnara.service.WalletCommandService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.math.BigDecimal;

/**
 * Provider actions on a client's wallet: top up and grant a package.
 *
 * <p>Thin controller — all rules and role checks live in
 * {@link WalletCommandService}. The {@code /provider/**} security rule already
 * restricts these routes to providers; the service re-checks ownership.
 */
@Controller
public class WalletController {

    private static final String PROVIDER_VIEW = "provider";
    private static final String CLIENTS_REDIRECT = "redirect:/provider?section=clients";

    private final WalletCommandService walletCommandService;
    private final ProviderPageModelAssembler providerPageModelAssembler;

    /**
     * Creates wallet controller.
     *
     * @param walletCommandService       service for provider wallet operations
     * @param providerPageModelAssembler assembler used to re-render the provider page on error
     */
    public WalletController(WalletCommandService walletCommandService,
                            ProviderPageModelAssembler providerPageModelAssembler) {
        this.walletCommandService = walletCommandService;
        this.providerPageModelAssembler = providerPageModelAssembler;
    }

    /**
     * Records a manual wallet top-up for a client.
     *
     * @param clientId    client whose wallet is credited
     * @param amount      positive amount in the provider currency
     * @param comment     optional note
     * @param currentUser authenticated provider
     * @param model       MVC model
     * @return redirect to the clients section, or the provider page with an error
     */
    @PostMapping("/provider/clients/{clientId}/wallet/top-up")
    public String topUp(@PathVariable Long clientId,
                        @RequestParam BigDecimal amount,
                        @RequestParam(required = false) String comment,
                        @CurrentUser User currentUser,
                        Model model) {
        return execute(currentUser, model,
                provider -> walletCommandService.topUp(provider, clientId, amount, comment));
    }

    /**
     * Grants a package of pre-paid sessions to a client.
     *
     * @param clientId      client who receives the package
     * @param offeringId    offering the sessions apply to
     * @param totalSessions number of sessions granted
     * @param price         total price paid, in the provider currency
     * @param comment       optional note
     * @param currentUser   authenticated provider
     * @param model         MVC model
     * @return redirect to the clients section, or the provider page with an error
     */
    @PostMapping("/provider/clients/{clientId}/packages")
    public String grantPackage(@PathVariable Long clientId,
                               @RequestParam Long offeringId,
                               @RequestParam Integer totalSessions,
                               @RequestParam BigDecimal price,
                               @RequestParam(required = false) String comment,
                               @CurrentUser User currentUser,
                               Model model) {
        return execute(currentUser, model,
                provider -> walletCommandService.grantPackage(
                        provider, clientId, offeringId, totalSessions, price, comment));
    }

    private String execute(User currentUser, Model model, WalletAction action) {
        try {
            action.execute(currentUser);
            return CLIENTS_REDIRECT;
        } catch (IllegalArgumentException exception) {
            providerPageModelAssembler.populate(model, currentUser);
            model.addAttribute("walletActionError", exception.getMessage());
            return PROVIDER_VIEW;
        }
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
