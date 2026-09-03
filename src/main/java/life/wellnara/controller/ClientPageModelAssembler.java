package life.wellnara.controller;

import life.wellnara.dto.AppointmentView;
import life.wellnara.dto.ClientPackageView;
import life.wellnara.dto.ClientWalletView;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserProfile;

import java.util.List;
import java.util.stream.Stream;
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
        User provider = clientOfferingService.getProviderOfClient(client);
        UserProfile providerProfile = userProfileService.findProfile(provider).orElse(null);

        model.addAttribute("offerings", clientOfferingService.getOfferingsOfClientProvider(client));
        model.addAttribute("providerName", userProfileService.resolveDisplayName(provider));
        model.addAttribute("providerWhatsapp", providerProfile != null ? providerProfile.getWhatsappUrl() : null);
        model.addAttribute("providerTelegram", providerProfile != null ? providerProfile.getTelegramUrl() : null);
        model.addAttribute("activePackages", walletQueryService.getActivePackagesOfClient(client));
        addClientName(model, client);
    }

    /**
     * Adds the appointments page data to the MVC model: the confirmed upcoming
     * appointments plus the provider-cancellation notifications the client either
     * re-books from ("choose another time") or dismisses.
     *
     * @param model  MVC model
     * @param client authenticated client
     */
    public void populateAppointments(Model model, User client) {
        List<AppointmentView> updates = appointmentService.getAppointmentViewsOfClient(client);
        List<AppointmentView> confirmed = appointmentService.getConfirmedAppointmentViewsOfClient(client);
        List<AppointmentView> providerCancellations = updates.stream()
                .filter(view -> view.getStatus() == AppointmentStatus.CANCELLED)
                .toList();

        // Rows on both lists can be package sessions (booked one at a time), so label
        // them with their session number like every other page.
        List<Long> labelledIds = Stream.concat(confirmed.stream(), providerCancellations.stream())
                .map(AppointmentView::getId)
                .toList();

        model.addAttribute("confirmedAppointments", confirmed);
        model.addAttribute("providerCancellations", providerCancellations);
        model.addAttribute("packageLabels", walletQueryService.packageLabelsForAppointments(labelledIds));
        addClientName(model, client);
    }

    /**
     * Adds the requests page data to the MVC model: everything still awaiting the
     * provider — pending package requests and {@code REQUESTED} single appointments.
     * Provider cancellations live on the appointments page, not here.
     *
     * @param model  MVC model
     * @param client authenticated client
     */
    public void populateRequests(Model model, User client) {
        List<AppointmentView> pendingAppointments = appointmentService.getAppointmentViewsOfClient(client).stream()
                .filter(view -> view.getStatus() == AppointmentStatus.REQUESTED)
                .toList();

        model.addAttribute("pendingPackages",
                walletQueryService.getPendingPackageRequestsOfClient(client));
        model.addAttribute("pendingAppointments", pendingAppointments);
        model.addAttribute("packageLabels", walletQueryService.packageLabelsForAppointments(
                pendingAppointments.stream().map(AppointmentView::getId).toList()));
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
        model.addAttribute("preferredCalendar",
                client.getPreferredCalendar() != null ? client.getPreferredCalendar().name() : "");
        addClientName(model, client);
    }

    /**
     * Adds the wallet page data to the MVC model: the client's money balance,
     * remaining package sessions and the full movement history.
     *
     * @param model  MVC model
     * @param client authenticated client
     */
    public void populateWallet(Model model, User client) {
        model.addAttribute("wallet", walletQueryService.getWalletOfClient(client));
        model.addAttribute("heldItems", walletQueryService.getHeldBreakdownOfClient(client));
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
        UserProfile providerProfile = userProfileService.findProfile(provider).orElse(null);
        ClientWalletView wallet = walletQueryService.getWalletOfClient(client);

        model.addAttribute("offering", offering);
        model.addAttribute("calendarTerms", appointmentService.getFreeCalendarTerms(provider));
        model.addAttribute("bookableDateOptions",
                appointmentService.getBookableDateOptions(provider, offering));
        model.addAttribute("packageSessionsLeft", packageSessionsLeftFor(client, offeringId));
        model.addAttribute("walletAvailable", wallet.getAvailable());
        model.addAttribute("walletCurrency", wallet.getCurrency());
        model.addAttribute("pendingPackageForOffering", hasPendingPackageFor(client, offeringId));
        model.addAttribute("providerWhatsapp", providerProfile != null ? providerProfile.getWhatsappUrl() : null);
        model.addAttribute("providerTelegram", providerProfile != null ? providerProfile.getTelegramUrl() : null);
        addClientName(model, client);
    }

    /**
     * Whether the client already has a package request for this offering awaiting
     * approval. Drives hiding the purchase form so a second request cannot stack a
     * duplicate hold on top of the pending one.
     */
    private boolean hasPendingPackageFor(User client, Long offeringId) {
        return walletQueryService.getPendingPackageRequestsOfClient(client).stream()
                .anyMatch(request -> request.getOfferingId().equals(offeringId));
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
