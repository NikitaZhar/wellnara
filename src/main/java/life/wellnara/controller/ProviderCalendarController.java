package life.wellnara.controller;

import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.exception.CalendarValidationException;
import life.wellnara.model.AvailabilityOverrideType;
import life.wellnara.model.User;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Controller for provider availability calendar actions.
 */
@Controller
public class ProviderCalendarController {

    private static final String PROVIDER_VIEW = "provider";
    private static final String CALENDAR_REDIRECT = "redirect:/provider?section=calendar";

    private final ProviderCalendarService providerCalendarService;
    private final ProviderPageModelAssembler providerPageModelAssembler;

    /**
     * Creates provider calendar controller.
     *
     * @param providerCalendarService service for provider calendar management
     * @param providerPageModelAssembler assembler for provider page model
     */
    public ProviderCalendarController(ProviderCalendarService providerCalendarService,
                                      ProviderPageModelAssembler providerPageModelAssembler) {
        this.providerCalendarService = providerCalendarService;
        this.providerPageModelAssembler = providerPageModelAssembler;
    }

    /**
     * Saves provider calendar availability settings.
     *
     * @param form provider calendar form
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return redirect to provider calendar section or provider page with validation errors
     */
    @PostMapping("/provider/calendar")
    public String saveCalendar(@ModelAttribute ProviderCalendarForm form,
                               @CurrentUser User currentUser,
                               Model model) {
        try {
            providerCalendarService.saveCalendar(currentUser, form);
            return CALENDAR_REDIRECT;
        } catch (CalendarValidationException exception) {
            providerPageModelAssembler.populate(model, currentUser);
            model.addAttribute("calendarForm", form);
            model.addAttribute("planningFrom", form.getPlanningFrom());
            model.addAttribute("planningTo", form.getPlanningTo());
            model.addAttribute("calendarErrors", exception.getFieldErrors());
            return PROVIDER_VIEW;
        }
    }

    /**
     * Creates one-time provider availability override.
     *
     * @param overrideDate override date
     * @param startTime override start time
     * @param endTime override end time
     * @param type override type
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return redirect to provider calendar section or provider page with validation error
     */
    @PostMapping("/provider/calendar/overrides")
    public String createAvailabilityOverride(@RequestParam LocalDate overrideDate,
                                             @RequestParam LocalTime startTime,
                                             @RequestParam LocalTime endTime,
                                             @RequestParam AvailabilityOverrideType type,
                                             @CurrentUser User currentUser,
                                             Model model) {
        try {
            providerCalendarService.createAvailabilityOverride(
                    currentUser,
                    overrideDate,
                    startTime,
                    endTime,
                    type
            );

            return CALENDAR_REDIRECT;
        } catch (IllegalArgumentException exception) {
            providerPageModelAssembler.populate(model, currentUser);
            model.addAttribute("calendarOverrideError", exception.getMessage());
            return PROVIDER_VIEW;
        }
    }

    /**
     * Deletes one-time provider availability override.
     *
     * @param overrideId override identifier
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return redirect to provider calendar section or provider page with validation error
     */
    @PostMapping("/provider/calendar/overrides/{overrideId}/delete")
    public String deleteAvailabilityOverride(@PathVariable Long overrideId,
                                             @CurrentUser User currentUser,
                                             Model model) {
        try {
            providerCalendarService.deleteAvailabilityOverride(currentUser, overrideId);
            return CALENDAR_REDIRECT;
        } catch (IllegalArgumentException exception) {
            providerPageModelAssembler.populate(model, currentUser);
            model.addAttribute("calendarOverrideError", exception.getMessage());
            return PROVIDER_VIEW;
        }
    }
}
