package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.exception.LocalizedException;
import life.wellnara.model.User;
import life.wellnara.service.ClientInvitationService;
import life.wellnara.service.email.InvitationNotificationService;
import life.wellnara.web.CurrentUser;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for client invitation from the provider clients page.
 */
@Controller
public class ClientInvitationController {

    private final ClientInvitationService clientInvitationService;
    private final InvitationNotificationService invitationNotificationService;
    private final MessageSource messageSource;

    /**
     * Creates the client invitation controller.
     *
     * @param clientInvitationService       client invitation flow
     * @param invitationNotificationService sends invitation emails
     * @param messageSource                 resolver of localized user-facing messages
     */
    public ClientInvitationController(ClientInvitationService clientInvitationService,
                                      InvitationNotificationService invitationNotificationService,
                                      MessageSource messageSource) {
        this.clientInvitationService = clientInvitationService;
        this.invitationNotificationService = invitationNotificationService;
        this.messageSource = messageSource;
    }

    private String msg(String code, Object... args) {
        return messageSource.getMessage(code, args, LocaleContextHolder.getLocale());
    }

    /**
     * Creates a client invitation and sends the registration link by email.
     *
     * <p>The result message is stashed in the session and shown on the clients
     * page after the redirect.
     *
     * @param email       invited client email
     * @param currentUser authenticated provider
     * @param session     current HTTP session
     * @return redirect to the clients page
     */
    @PostMapping("/provider/invite-client")
    public String inviteClient(@RequestParam String email,
                               @CurrentUser User currentUser,
                               HttpSession session) {
        try {
            String token = clientInvitationService.invite(currentUser, email);
            invitationNotificationService.sendClientInvitation(email, token);

            session.setAttribute("clientInviteSuccessMessage", msg("error.invite.client.sent", email));
            session.removeAttribute("clientInviteError");
        } catch (IllegalArgumentException exception) {
            session.setAttribute("clientInviteError",
                    LocalizedException.resolve(exception, messageSource, LocaleContextHolder.getLocale()));
            session.removeAttribute("clientInviteSuccessMessage");
        } catch (MailException exception) {
            session.setAttribute("clientInviteError", msg("error.invite.mailFailed", email));
            session.removeAttribute("clientInviteSuccessMessage");
        }

        return "redirect:/provider/clients";
    }
}
