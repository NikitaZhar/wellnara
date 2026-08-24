package life.wellnara.controller;

import life.wellnara.dto.AppointmentView;
import life.wellnara.dto.ClientRow;
import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.OfferingService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.service.ProviderClientService;
import life.wellnara.service.WalletQueryService;
import life.wellnara.service.UserProfileService;
import life.wellnara.service.time.ApplicationTimeService;
import life.wellnara.service.wallet.CurrencyCodes;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import java.util.Map;

/**
 * Assembles the model data for the provider pages.
 *
 * <p>Each provider section is a separate page and loads only its own data, so
 * this assembler exposes one {@code populate*} method per page rather than a
 * single method that fills everything. Every page shows the provider name in
 * its header, so each method also adds it through a shared helper.
 */
@Component
public class ProviderPageModelAssembler {

    private final ProviderClientService providerClientService;
    private final OfferingService offeringService;
    private final ProviderCalendarService providerCalendarService;
    private final AppointmentService appointmentService;
    private final ApplicationTimeService applicationTimeService;
    private final UserProfileService userProfileService;
    private final WalletQueryService walletQueryService;

    /**
     * Creates provider page model assembler.
     *
     * @param providerClientService service for provider client operations
     * @param offeringService service for offering management
     * @param providerCalendarService service for provider calendar management
     * @param appointmentService service for appointment operations
     * @param applicationTimeService service for application time calculations
     * @param userProfileService service for user personal data
     * @param walletQueryService service for read-only wallet balances
     */
    public ProviderPageModelAssembler(ProviderClientService providerClientService,
                                      OfferingService offeringService,
                                      ProviderCalendarService providerCalendarService,
                                      AppointmentService appointmentService,
                                      ApplicationTimeService applicationTimeService,
                                      UserProfileService userProfileService,
                                      WalletQueryService walletQueryService) {
        this.providerClientService = providerClientService;
        this.offeringService = offeringService;
        this.providerCalendarService = providerCalendarService;
        this.appointmentService = appointmentService;
        this.applicationTimeService = applicationTimeService;
        this.userProfileService = userProfileService;
        this.walletQueryService = walletQueryService;
    }

    /**
     * Adds the clients page data to the MVC model.
     *
     * @param model MVC model
     * @param provider authenticated provider
     */
    public void populateClients(Model model, User provider) {
        model.addAttribute("clients", buildClientRows(provider));
        addProviderName(model, provider);
    }

    /**
     * Adds the offerings page data to the MVC model: the provider's offerings and
     * the provider wallet currency new offering prices are quoted in.
     *
     * @param model MVC model
     * @param provider authenticated provider
     */
    public void populateOfferings(Model model, User provider) {
        model.addAttribute("offerings", offeringService.getOfferingsOfProvider(provider));
        model.addAttribute("providerCurrency", provider.getCurrency());
        addProviderName(model, provider);
    }

    /**
     * Adds the appointments page data to the MVC model: pending requests and
     * confirmed appointments.
     *
     * @param model MVC model
     * @param provider authenticated provider
     */
    public void populateAppointments(Model model, User provider) {
        List<AppointmentView> requests = appointmentService.getAppointmentViewsOfProvider(provider);
        List<AppointmentView> confirmed = appointmentService.getConfirmedAppointmentViewsOfProvider(provider);

        // Package session numbers are shown on both the requests and the upcoming
        // appointments, so label every appointment id rendered on either page.
        List<Long> labelledIds = Stream.concat(requests.stream(), confirmed.stream())
                .map(AppointmentView::getId)
                .toList();

        model.addAttribute("appointments", requests);
        model.addAttribute("confirmedAppointments", confirmed);
        model.addAttribute("packageLabels", walletQueryService.packageLabelsForAppointments(labelledIds));
        model.addAttribute("packageRequests", walletQueryService.getPendingPackageRequestsForProvider(provider));
        addProviderName(model, provider);
    }

    /**
     * Adds the profile page data to the MVC model: account fields, personal data
     * and the currencies supported for the provider currency selector.
     *
     * @param model MVC model
     * @param provider authenticated provider
     */
    public void populateProfile(Model model, User provider) {
        UserProfile profile = userProfileService.findProfile(provider).orElse(null);

        model.addAttribute("providerLogin", provider.getUsername());
        model.addAttribute("providerEmail", provider.getEmail());
        model.addAttribute("profileFirstName", profile != null ? profile.getFirstName() : "");
        model.addAttribute("profileLastName", profile != null ? profile.getLastName() : "");
        model.addAttribute("profilePhone", profile != null ? profile.getPhone() : "");
        model.addAttribute("providerCurrency", provider.getCurrency());
        model.addAttribute("supportedCurrencies", CurrencyCodes.SUPPORTED);
        addProviderName(model, provider);
    }

    /**
     * Adds the availability page data to the MVC model: the calendar form, its
     * planning window, the provider's current calendar date, the generated free
     * terms and the one-time availability overrides.
     *
     * @param model MVC model
     * @param provider authenticated provider
     */
    public void populateAvailability(Model model, User provider) {
        ProviderCalendarForm calendarForm =
                providerCalendarService.getLatestCalendarForm(provider);

        model.addAttribute("calendarForm", calendarForm);
        model.addAttribute("planningFrom", calendarForm.getPlanningFrom());
        model.addAttribute("planningTo", calendarForm.getPlanningTo());
        model.addAttribute("today", applicationTimeService.currentProviderCalendarDate(provider));
        model.addAttribute("calendarTerms", appointmentService.getFreeCalendarTerms(provider));
        model.addAttribute("availabilityOverrides", providerCalendarService.getAvailabilityOverrides(provider));
        addProviderName(model, provider);
    }

    private void addProviderName(Model model, User provider) {
        model.addAttribute("providerName", userProfileService.resolveDisplayName(provider));
    }

    private List<ClientRow> buildClientRows(User provider) {
        List<ProviderClientLink> links = providerClientService.getClientsOfProvider(provider);

        List<User> clients = links.stream()
                .map(ProviderClientLink::getClient)
                .toList();

        Map<Long, UserProfile> profilesByUserId = userProfileService.loadProfilesByUserId(clients);
        Map<Long, BigDecimal> availableByClientId = walletQueryService.getClientBalances(provider);
        String providerCurrency = provider.getCurrency();

        return links.stream()
                .map(link -> {
                    User client = link.getClient();
                    UserProfile profile = profilesByUserId.get(client.getId());
                    BigDecimal available = availableByClientId.get(client.getId());
                    return new ClientRow(
                            client.getId(),
                            userProfileService.displayNameOf(client, profile),
                            client.getEmail(),
                            profile != null ? profile.getPhone() : null,
                            link.getInvitedAt(),
                            available != null ? available : BigDecimal.ZERO,
                            providerCurrency
                    );
                })
                .toList();
    }
}
