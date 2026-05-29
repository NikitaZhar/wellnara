package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.service.SessionUserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for provider main page access.
 */
@Controller
public class ProviderController {

    private static final String LOGIN_REDIRECT = "redirect:/auth/login";
    private static final String PROVIDER_VIEW = "provider";

    private final ProviderCalendarService providerCalendarService;
    private final AppointmentService appointmentService;
    private final SessionUserService sessionUserService;
    private final ProviderPageModelAssembler providerPageModelAssembler;

    /**
     * Creates provider controller.
     *
     * @param providerCalendarService service for provider calendar management
     * @param appointmentService service for appointment operations
     * @param sessionUserService service for authenticated session user access
     * @param providerPageModelAssembler assembler for provider page model
     */
    public ProviderController(ProviderCalendarService providerCalendarService,
                              AppointmentService appointmentService,
                              SessionUserService sessionUserService,
                              ProviderPageModelAssembler providerPageModelAssembler) {
        this.providerCalendarService = providerCalendarService;
        this.appointmentService = appointmentService;
        this.sessionUserService = sessionUserService;
        this.providerPageModelAssembler = providerPageModelAssembler;
    }

    /**
     * Shows provider page for authenticated provider user.
     *
     * @param session current HTTP session
     * @param model MVC model
     * @return provider page view name or redirect to login page
     */
    @GetMapping("/provider")
    public String showPage(HttpSession session, Model model) {
        User currentUser = sessionUserService.requireProvider(session);

        if (currentUser == null) {
            return LOGIN_REDIRECT;
        }

        providerCalendarService.deleteExpiredAvailabilityPeriods(currentUser);
        providerCalendarService.deleteExpiredAvailabilityOverrides(currentUser);
        appointmentService.deleteExpiredUnpaidAppointments();

        moveSessionAttributeToModel(session, model, "clientInviteLink");
        moveSessionAttributeToModel(session, model, "clientInviteError");

        providerPageModelAssembler.populate(model, currentUser);

        return PROVIDER_VIEW;
    }

    private void moveSessionAttributeToModel(HttpSession session,
                                             Model model,
                                             String attributeName) {
        Object attributeValue = session.getAttribute(attributeName);

        if (attributeValue != null) {
            model.addAttribute(attributeName, attributeValue);
            session.removeAttribute(attributeName);
        }
    }
}