package life.wellnara.controller;

import life.wellnara.dto.ClientRow;
import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserProfile;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.OfferingService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.service.ProviderClientService;
import life.wellnara.service.UserProfileService;
import life.wellnara.service.time.ApplicationTimeService;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import java.util.List;
import java.util.Map;

/**
 * Assembles model data required by the provider page.
 */
@Component
public class ProviderPageModelAssembler {

    private final ProviderClientService providerClientService;
    private final OfferingService offeringService;
    private final ProviderCalendarService providerCalendarService;
    private final AppointmentService appointmentService;
    private final ApplicationTimeService applicationTimeService;
    private final UserProfileService userProfileService;

    /**
     * Creates provider page model assembler.
     *
     * @param providerClientService service for provider client operations
     * @param offeringService service for offering management
     * @param providerCalendarService service for provider calendar management
     * @param appointmentService service for appointment operations
     * @param applicationTimeService service for application time calculations
     * @param userProfileService service for user personal data
     */
    public ProviderPageModelAssembler(ProviderClientService providerClientService,
                                      OfferingService offeringService,
                                      ProviderCalendarService providerCalendarService,
                                      AppointmentService appointmentService,
                                      ApplicationTimeService applicationTimeService,
                                      UserProfileService userProfileService) {
        this.providerClientService = providerClientService;
        this.offeringService = offeringService;
        this.providerCalendarService = providerCalendarService;
        this.appointmentService = appointmentService;
        this.applicationTimeService = applicationTimeService;
        this.userProfileService = userProfileService;
    }

    /**
     * Adds provider page data to MVC model.
     *
     * @param model MVC model
     * @param provider authenticated provider
     */
    public void populate(Model model, User provider) {
        model.addAttribute("clients", buildClientRows(provider));
        model.addAttribute("offerings", offeringService.getOfferingsOfProvider(provider));
        model.addAttribute("providerName", userProfileService.resolveDisplayName(provider));
        model.addAttribute("appointments", appointmentService.getAppointmentViewsOfProvider(provider));
        model.addAttribute("confirmedAppointments",
                appointmentService.getConfirmedAppointmentViewsOfProvider(provider));

        populateProfileModel(model, provider);
        populateCalendarModel(model, provider);
    }

    private void populateProfileModel(Model model, User provider) {
        UserProfile profile = userProfileService.findProfile(provider).orElse(null);

        model.addAttribute("providerLogin", provider.getUsername());
        model.addAttribute("providerEmail", provider.getEmail());
        model.addAttribute("profileFirstName", profile != null ? profile.getFirstName() : "");
        model.addAttribute("profileLastName", profile != null ? profile.getLastName() : "");
        model.addAttribute("profilePhone", profile != null ? profile.getPhone() : "");
    }

    private List<ClientRow> buildClientRows(User provider) {
        List<ProviderClientLink> links = providerClientService.getClientsOfProvider(provider);

        List<User> clients = links.stream()
                .map(ProviderClientLink::getClient)
                .toList();

        Map<Long, UserProfile> profilesByUserId = userProfileService.loadProfilesByUserId(clients);

        return links.stream()
                .map(link -> {
                    User client = link.getClient();
                    UserProfile profile = profilesByUserId.get(client.getId());
                    return new ClientRow(
                            client.getId(),
                            userProfileService.displayNameOf(client, profile),
                            client.getEmail(),
                            profile != null ? profile.getPhone() : null,
                            link.getInvitedAt()
                    );
                })
                .toList();
    }

    /**
     * Adds provider calendar section data to MVC model.
     *
     * @param model MVC model
     * @param provider authenticated provider
     */
    private void populateCalendarModel(Model model, User provider) {
        ProviderCalendarForm calendarForm =
                providerCalendarService.getLatestCalendarForm(provider);

        model.addAttribute("calendarForm", calendarForm);
        model.addAttribute("planningFrom", calendarForm.getPlanningFrom());
        model.addAttribute("planningTo", calendarForm.getPlanningTo());
        model.addAttribute("today", applicationTimeService.currentProviderCalendarDate(provider));
        model.addAttribute("calendarTerms", appointmentService.getFreeCalendarTerms(provider));
        model.addAttribute("availabilityOverrides", providerCalendarService.getAvailabilityOverrides(provider));
    }
}
