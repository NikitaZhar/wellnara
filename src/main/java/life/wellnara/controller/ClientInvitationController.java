package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.ClientInvitationService;
import life.wellnara.service.SessionUserService;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for client invitation from provider page.
 */
@Controller
public class ClientInvitationController {

	private final ClientInvitationService clientInvitationService;
	private final SessionUserService sessionUserService;

	/**
	 * Creates client invitation controller.
	 *
	 * @param clientInvitationService service for client invitations
	 * @param sessionUserService service for authenticated session user access
	 */
	public ClientInvitationController(ClientInvitationService clientInvitationService,
			SessionUserService sessionUserService) {
		this.clientInvitationService = clientInvitationService;
		this.sessionUserService = sessionUserService;
	}

	/**
	 * Creates client invitation and stores registration link in session.
	 *
	 * @param email invited client email
	 * @param session current HTTP session
	 * @param model MVC model
	 * @return redirect to provider page or provider page with validation error
	 */
	@PostMapping("/provider/invite-client")
	public String inviteClient(@RequestParam String email,
			HttpSession session) {
		User currentUser = sessionUserService.requireProvider(session);

		if (currentUser == null) {
			return "redirect:/auth/login";
		}

		try {
			String token = clientInvitationService.invite(currentUser, email);
			String link = "http://localhost:8080/client/register?token=" + token;

			session.setAttribute("clientInviteLink", link);
			session.removeAttribute("clientInviteError");

		} catch (IllegalArgumentException exception) {
			session.setAttribute("clientInviteError", exception.getMessage());
			session.removeAttribute("clientInviteLink");
		}

		return "redirect:/provider";
	}    
}