package life.wellnara.controller;

import life.wellnara.dto.ClientWalletView;
import life.wellnara.dto.TopUpResult;
import life.wellnara.dto.WalletHistoryRow;
import life.wellnara.exception.LocalizedException;
import life.wellnara.model.User;
import life.wellnara.service.UserProfileService;
import life.wellnara.service.WalletCommandService;
import life.wellnara.service.WalletQueryService;
import life.wellnara.web.CurrentUser;
import org.springframework.beans.propertyeditors.StringTrimmerEditor;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.WebDataBinder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Provider actions on a client's wallet: view the client profile and top the
 * wallet up.
 *
 * <p>Thin controller — all rules and role checks live in the services. The
 * {@code /provider/**} security rule already restricts these routes to
 * providers; the services re-check ownership. The read page
 * ({@code GET .../wallet}) is served from {@link WalletQueryService}; the
 * top-up ({@code POST .../wallet/top-up}) from {@link WalletCommandService} and
 * answers with JSON so the profile page updates the balance and payment history
 * in place, without a reload.
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
     * Shows a client's profile to the provider: contact details, balance, the
     * payment history and the top-up action.
     *
     * @param clientId    client whose profile is shown
     * @param currentUser authenticated provider
     * @param model       MVC model
     * @return the client profile page, or a redirect to the clients list if the client is not linked
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
     * Records a manual wallet top-up for a client and answers with the refreshed
     * balances and the newly created movement, for an in-place page update.
     *
     * @param clientId    client whose wallet is credited
     * @param amount      positive amount in the provider currency
     * @param comment     optional note
     * @param currentUser authenticated provider
     * @return {@code 200} with the {@link TopUpResult}; {@code 400} with an {@code error} message
     *         if the amount is invalid or the client is not the provider's
     */
    @PostMapping("/provider/clients/{clientId}/wallet/top-up")
    @ResponseBody
    public ResponseEntity<?> topUp(@PathVariable Long clientId,
                                   @RequestParam BigDecimal amount,
                                   @RequestParam(required = false) String comment,
                                   @CurrentUser User currentUser) {
        try {
            walletCommandService.topUp(currentUser, clientId, amount, comment);
            ClientWalletView view = walletQueryService.getWalletForProvider(currentUser, clientId);
            WalletHistoryRow newest = view.getHistory().get(0);
            TopUpResult result = new TopUpResult(
                    view.getAvailable(),
                    view.getHeld(),
                    view.getCurrency(),
                    new TopUpResult.Entry(newest.getTimestamp(), newest.getTypeLabel(),
                            newest.getAmount(), newest.getCurrency()));
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException exception) {
            String message = LocalizedException.resolve(exception, messageSource, LocaleContextHolder.getLocale());
            return ResponseEntity.badRequest().body(Map.of("error", message));
        }
    }

    private void populateWalletPage(Model model, User provider, Long clientId) {
        model.addAttribute("wallet", walletQueryService.getWalletForProvider(provider, clientId));
        model.addAttribute("clientId", clientId);
        model.addAttribute("providerName", userProfileService.resolveDisplayName(provider));
    }
}
