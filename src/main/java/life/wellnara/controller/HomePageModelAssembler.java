package life.wellnara.controller;

import life.wellnara.dto.AppointmentView;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.ClientOfferingService;
import life.wellnara.service.OfferingService;
import life.wellnara.service.ProviderClientService;
import life.wellnara.service.UserProfileService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;

/**
 * Assembles model data for the Home page (provider and client).
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
    private final ClientOfferingService clientOfferingService;
    private final UserProfileService userProfileService;

    /**
     * Creates the home page model assembler.
     *
     * @param appointmentService    service for appointment operations
     * @param offeringService       service for provider offering management
     * @param providerClientService service for provider-client links
     * @param clientOfferingService service for client access to provider offerings
     * @param userProfileService    service for user personal data
     */
    public HomePageModelAssembler(AppointmentService appointmentService,
                                  OfferingService offeringService,
                                  ProviderClientService providerClientService,
                                  ClientOfferingService clientOfferingService,
                                  UserProfileService userProfileService) {
        this.appointmentService = appointmentService;
        this.offeringService = offeringService;
        this.providerClientService = providerClientService;
        this.clientOfferingService = clientOfferingService;
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

    /**
     * Adds client Home data to the MVC model.
     *
     * @param model  MVC model
     * @param client authenticated client
     */
    public void populateClientHome(Model model, User client) {
        List<AppointmentView> pendingRequests = appointmentService
                .getAppointmentViewsOfClient(client).stream()
                .filter(view -> view.getStatus() == AppointmentStatus.REQUESTED)
                .toList();

        List<AppointmentView> upcomingAppointments =
                appointmentService.getConfirmedAppointmentViewsOfClient(client);

        List<Offering> availableOfferings = availableOfferingsOf(client);

        model.addAttribute("clientName", userProfileService.resolveDisplayName(client));
        model.addAttribute("pendingRequests", pendingRequests);
        model.addAttribute("pendingRequestCount", pendingRequests.size());
        model.addAttribute("upcomingAppointments", upcomingAppointments);
        model.addAttribute("availableOfferings", availableOfferings);
        model.addAttribute("availableOfferingCount", availableOfferings.size());
    }

    /**
     * Offerings the client can request, or an empty list when the client has no
     * provider link yet. Home must always render, so a missing link is treated
     * as "nothing to show" rather than an error.
     */
    private List<Offering> availableOfferingsOf(User client) {
        try {
            return clientOfferingService.getOfferingsOfClientProvider(client);
        } catch (IllegalArgumentException noProviderLink) {
            return List.of();
        }
    }
}
