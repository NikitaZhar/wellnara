package life.wellnara.controller;

import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.OfferingService;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.service.ProviderClientService;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Assembles model data required by the provider page.
 */
@Component
public class ProviderPageModelAssembler {

    private final ProviderClientService providerClientService;
    private final OfferingService offeringService;
    private final ProviderCalendarService providerCalendarService;
    private final AppointmentService appointmentService;

    /**
     * Creates provider page model assembler.
     *
     * @param providerClientService service for provider client operations
     * @param offeringService service for offering management
     * @param providerCalendarService service for provider calendar management
     * @param appointmentService service for appointment operations
     */
    public ProviderPageModelAssembler(ProviderClientService providerClientService,
                                      OfferingService offeringService,
                                      ProviderCalendarService providerCalendarService,
                                      AppointmentService appointmentService) {
        this.providerClientService = providerClientService;
        this.offeringService = offeringService;
        this.providerCalendarService = providerCalendarService;
        this.appointmentService = appointmentService;
    }

    /**
     * Adds provider page data to MVC model.
     *
     * @param model MVC model
     * @param provider authenticated provider
     */
    public void populate(Model model, User provider) {
        model.addAttribute("clients", providerClientService.getClientsOfProvider(provider));
        model.addAttribute("offerings", offeringService.getOfferingsOfProvider(provider));
        model.addAttribute("providerName", provider.getUsername());
        model.addAttribute("appointments", appointmentService.getAppointmentViewsOfProvider(provider));
        model.addAttribute("confirmedAppointments",
                appointmentService.getConfirmedAppointmentViewsOfProvider(provider));

        populateCalendarModel(model, provider);
    }

    private void populateCalendarModel(Model model, User provider) {
        ProviderCalendarForm calendarForm = providerCalendarService.getLatestCalendarForm(provider);

        model.addAttribute("calendarForm", calendarForm);
        model.addAttribute("planningFrom", calendarForm.getPlanningFrom());
        model.addAttribute("planningTo", calendarForm.getPlanningTo());
        model.addAttribute("calendarTerms", appointmentService.getFreeCalendarTerms(provider));
        model.addAttribute("availabilityOverrides",
                providerCalendarService.getAvailabilityOverrides(provider));
    }
}