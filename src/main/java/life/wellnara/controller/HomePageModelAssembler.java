package life.wellnara.controller;

import life.wellnara.dto.AppointmentView;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.OfferingService;
import life.wellnara.service.ProviderClientService;
import life.wellnara.service.UserProfileService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

/**
 * Assembles model data for the Home page.
 *
 * <p>Variant A: every tile is derived from the current state via existing
 * services (appointments, offerings, clients). No notification/event table is
 * introduced — the persistent activity feed is a later version. The wallet tile
 * is added here once phase 3 lands.
 */
@Component
public class HomePageModelAssembler {

    private final AppointmentService appointmentService;
    private final OfferingService offeringService;
    private final ProviderClientService providerClientService;
    private final UserProfileService userProfileService;

    /**
     * Creates the home page model assembler.
     *
     * @param appointmentService    service for appointment operations
     * @param offeringService       service for offering management
     * @param providerClientService service for provider-client links
     * @param userProfileService    service for user personal data
     */
    public HomePageModelAssembler(AppointmentService appointmentService,
                                  OfferingService offeringService,
                                  ProviderClientService providerClientService,
                                  UserProfileService userProfileService) {
        this.appointmentService = appointmentService;
        this.offeringService = offeringService;
        this.providerClientService = providerClientService;
        this.userProfileService = userProfileService;
    }

    /**
     * Adds provider Home data to the MVC model.
     *
     * @param model    MVC model
     * @param provider authenticated provider
     */
    public void populateProviderHome(Model model, User provider) {
        List<AppointmentView> pendingRequests = appointmentService
                .getAppointmentViewsOfProvider(provider).stream()
                .filter(view -> view.getStatus() == AppointmentStatus.REQUESTED)
                .toList();

        List<AppointmentView> upcomingAppointments =
                appointmentService.getConfirmedAppointmentViewsOfProvider(provider);

        long activeOfferingCount = offeringService.getOfferingsOfProvider(provider).stream()
                .filter(Offering::isActive)
                .count();

        int clientCount = providerClientService.getClientsOfProvider(provider).size();

        model.addAttribute("providerName", userProfileService.resolveDisplayName(provider));
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingRequestCount", pendingRequests.size());
        model.addAttribute("upcomingAppointments", upcomingAppointments);
        model.addAttribute("activeOfferingCount", activeOfferingCount);
        model.addAttribute("clientCount", clientCount);
    }
}
