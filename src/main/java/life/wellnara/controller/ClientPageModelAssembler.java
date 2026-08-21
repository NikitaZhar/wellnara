package life.wellnara.controller;

import life.wellnara.dto.ClientPackageView;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.ClientOfferingService;
import life.wellnara.service.UserProfileService;
import life.wellnara.service.WalletQueryService;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Assembles the model data for the client pages.
 *
 * <p>The client cabinet is split into separate pages (offerings, appointments,
 * profile) plus a single-offering booking page. Each page loads only its own
 * data through a dedicated {@code populate*} method. The client display name is
 * shown in every page header, so each method adds it through a shared helper.
 */
@Component
public class ClientPageModelAssembler {

    private final ClientOfferingService clientOfferingService;
    private final AppointmentService appointmentService;
    private final UserProfileService userProfileService;
    private final WalletQueryService walletQueryService;

    /**
     * Creates client page model assembler.
     *
     * @param clientOfferingService service for client access to provider offerings
     * @param appointmentService    service for appointment queries
     * @param userProfileService    service for user personal data
     * @param walletQueryService    service for the client's active packages
     */
    public ClientPageModelAssembler(ClientOfferingService clientOfferingService,
                                    AppointmentService appointmentService,
                                    UserProfileService userProfileService,
                                    WalletQueryService walletQueryService) {
        this.clientOfferingService = clientOfferingService;
        this.appointmentService = appointmentService;
        this.userProfileService = userProfileService;
        this.walletQueryService = walletQueryService;
    }

    /**
     * Adds the offerings page data to the MVC model: the offerings the client's
     * provider makes available.
     *
     * @param model  MVC model
     * @param client authenticated client
     */
    public void populateOfferings(Model model, User client) {
        model.addAttribute("offerings", clientOfferingService.getOfferingsOfClientProvider(client));
        model.addAttribute("providerName",
                userProfileService.resolveDisplayName(clientOfferingService.getProviderOfClient(client)));
        model.addAttribute("activePackages", walletQueryService.getActivePackagesOfClient(client));
        model.addAttribute("pendingPackages", walletQueryService.getPendingPackageRequestsOfClient(client));
        addClientName(model, client);
    }

    /**
     * Adds the appointments page data to the MVC model: the client's requests and
     * notifications plus the confirmed appointments.
     *
     * @param model  MVC model
     * @param client authenticated client
     */
    public void populateAppointments(Model model, User client) {
        model.addAttribute("appointments", appointmentService.getAppointmentViewsOfClient(client));
        model.addAttribute("confirmedAppointments",
                appointmentService.getConfirmedAppointmentViewsOfClient(client));
        addClientName(model, client);
    }

    /**
     * Adds the profile page data to the MVC model: account fields and personal data.
     *
     * @param model  MVC model
     * @param client authenticated client
     */
    public void populateProfile(Model model, User client) {
        UserProfile profile = userProfileService.findProfile(client).orElse(null);

        model.addAttribute("clientLogin", client.getUsername());
        model.addAttribute("clientEmail", client.getEmail());
        model.addAttribute("profileFirstName", profile != null ? profile.getFirstName() : "");
        model.addAttribute("profileLastName", profile != null ? profile.getLastName() : "");
        model.addAttribute("profilePhone", profile != null ? profile.getPhone() : "");
        addClientName(model, client);
    }

    /**
     * Adds the single-offering booking page data to the MVC model: the offering,
     * the provider's free calendar terms and the bookable date options. Used both
     * to show the page and to re-render it when a booking request is rejected.
     *
     * @param model      MVC model
     * @param client     authenticated client
     * @param offeringId offering the client is viewing
     */
    public void populateOffering(Model model, User client, Long offeringId) {
        Offering offering = clientOfferingService.getOfferingOfClientProvider(client, offeringId);
        User provider = offering.getProvider();

        model.addAttribute("offering", offering);
        model.addAttribute("calendarTerms", appointmentService.getFreeCalendarTerms(provider));
        model.addAttribute("bookableDateOptions",
                appointmentService.getBookableDateOptions(provider, offering));
        model.addAttribute("packageSessionsLeft", packageSessionsLeftFor(client, offeringId));
        addClientName(model, client);
    }

    /**
     * Sessions the client can still book from a package covering this offering; 0
     * when none. Drives the "this booking uses a package session" note.
     */
    private int packageSessionsLeftFor(User client, Long offeringId) {
        return walletQueryService.getActivePackagesOfClient(client).stream()
                .filter(pkg -> pkg.getOfferingId().equals(offeringId))
                .mapToInt(ClientPackageView::getRemaining)
                .findFirst()
                .orElse(0);
    }

    private void addClientName(Model model, User client) {
        model.addAttribute("clientName", userProfileService.resolveDisplayName(client));
    }
}
