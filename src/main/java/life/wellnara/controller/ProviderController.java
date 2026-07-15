package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Controller for provider main page access.
 */
@Controller
public class ProviderController {

    private static final String PROVIDER_VIEW = "provider";

    private final ProviderCalendarService providerCalendarService;
    private final AppointmentService appointmentService;
    private final ProviderPageModelAssembler providerPageModelAssembler;

    /**
     * Creates provider controller.
     *
     * @param providerCalendarService service for provider calendar management
     * @param appointmentService service for appointment operations
     * @param providerPageModelAssembler assembler for provider page model
     */
    public ProviderController(ProviderCalendarService providerCalendarService,
                              AppointmentService appointmentService,
                              ProviderPageModelAssembler providerPageModelAssembler) {
        this.providerCalendarService = providerCalendarService;
        this.appointmentService = appointmentService;
        this.providerPageModelAssembler = providerPageModelAssembler;
    }

    /**
     * Shows provider page for authenticated provider user.
     *
     * @param currentUser authenticated provider
     * @param session current HTTP session
     * @param model MVC model
     * @return provider page view name
     */
    @GetMapping("/provider")
    public String showPage(@CurrentUser User currentUser, HttpSession session, Model model) {
        providerCalendarService.deleteExpiredAvailabilityPeriods(currentUser);
        providerCalendarService.deleteExpiredAvailabilityOverrides(currentUser);
        appointmentService.deleteExpiredUnpaidAppointments();

        moveSessionAttributeToModel(session, model, "clientInviteSuccessMessage");
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
