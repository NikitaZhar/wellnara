package life.wellnara.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import life.wellnara.model.User;
import life.wellnara.security.SecuritySessionService;
import life.wellnara.service.ProviderInvitationService;
import life.wellnara.service.wallet.CurrencyCodes;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for specialist registration by invitation token.
 */
@Controller
public class ProviderRegistrationController {

    private final ProviderInvitationService service;
    private final SecuritySessionService securitySessionService;

    /**
     * Creates provider registration controller.
     *
     * @param service                provider invitation service
     * @param securitySessionService service that establishes the security context
     */
    public ProviderRegistrationController(ProviderInvitationService service,
    		SecuritySessionService securitySessionService) {
        this.service = service;
        this.securitySessionService = securitySessionService;
    }

    /**
     * Shows specialist registration page.
     *
     * @param token invitation token
     * @param model MVC model
     * @return registration page view name
     */
    @GetMapping("/provider/register")
    public String showRegisterPage(@RequestParam String token, Model model) {
        try {
            String email = service.getEmailByToken(token);
            model.addAttribute("token", token);
            model.addAttribute("email", email);
            addCurrencyAttributes(model, CurrencyCodes.DEFAULT);
            return "provider-register";
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "login";
        }
    }

    /**
     * Registers provider by invitation token.
     *
     * @param token           invitation token
     * @param name            provider name
     * @param password        provider password
     * @param confirmPassword repeated provider password
     * @param firstName       provider first name
     * @param lastName        provider last name
     * @param phone           provider phone number, optional
     * @param currency        provider wallet currency (ISO 4217)
     * @param request         current request
     * @param response        current response
     * @param model           MVC model
     * @return redirect to home on success or registration page on validation error
     */
    @PostMapping("/provider/register")
    public String register(@RequestParam String token,
                           @RequestParam String name,
                           @RequestParam String password,
                           @RequestParam String confirmPassword,
                           @RequestParam String firstName,
                           @RequestParam String lastName,
                           @RequestParam(required = false) String phone,
                           @RequestParam String currency,
                           HttpServletRequest request,
                           HttpServletResponse response,
                           Model model) {
        String email;

        try {
            email = service.getEmailByToken(token);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            return "login";
        }

        if (!password.equals(confirmPassword)) {
            return renderRegisterError(model, token, email, name, firstName, lastName, phone,
                    currency, "Passwords do not match");
        }

        try {
            User registeredUser = service.register(token, name, password, firstName, lastName, phone, currency);
            securitySessionService.establish(registeredUser, request, response);
            return "redirect:/home";
        } catch (IllegalArgumentException exception) {
            return renderRegisterError(model, token, email, name, firstName, lastName, phone,
                    currency, exception.getMessage());
        }
    }

    private String renderRegisterError(Model model,
                                       String token,
                                       String email,
                                       String name,
                                       String firstName,
                                       String lastName,
                                       String phone,
                                       String currency,
                                       String error) {
        model.addAttribute("token", token);
        model.addAttribute("email", email);
        model.addAttribute("name", name);
        model.addAttribute("firstName", firstName);
        model.addAttribute("lastName", lastName);
        model.addAttribute("phone", phone);
        addCurrencyAttributes(model, currency);
        model.addAttribute("error", error);
        return "provider-register";
    }

    private void addCurrencyAttributes(Model model, String selectedCurrency) {
        model.addAttribute("supportedCurrencies", CurrencyCodes.SUPPORTED);
        model.addAttribute("selectedCurrency", selectedCurrency);
    }
}
