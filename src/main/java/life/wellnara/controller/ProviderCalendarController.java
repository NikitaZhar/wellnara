package life.wellnara.controller;

import life.wellnara.dto.CalendarTerm;
import life.wellnara.dto.ProviderCalendarForm;
import life.wellnara.exception.CalendarValidationException;
import life.wellnara.model.User;
import life.wellnara.service.ProviderCalendarService;
import life.wellnara.web.CurrentUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * Controller for provider availability calendar actions.
 *
 * <p>The whole calendar — planning period, weekly rules and one-time changes —
 * is committed by a single Save. While editing, the term list is refreshed
 * through a read-only preview so the provider sees the result before saving.
 */
@Controller
public class ProviderCalendarController {

    private static final String AVAILABILITY_VIEW = "provider-availability";
    private static final String AVAILABILITY_REDIRECT = "redirect:/provider/availability";

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
     * Saves the whole provider calendar — planning period, weekly rules and
     * one-time changes — atomically.
     *
     * <p>On validation failure the availability page is re-rendered with the
     * submitted form kept in place (so the provider's input is not lost) and the
     * field errors shown.
     *
     * @param form calendar form including staged one-time changes
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return redirect to the availability page, or that page with validation errors
     */
    @PostMapping("/provider/calendar")
    public String saveCalendar(@ModelAttribute ProviderCalendarForm form,
                               @CurrentUser User currentUser,
                               Model model) {
        try {
            providerCalendarService.saveCalendar(currentUser, form);
            return AVAILABILITY_REDIRECT;
        } catch (CalendarValidationException exception) {
            providerPageModelAssembler.populateAvailability(model, currentUser);
            model.addAttribute("calendarForm", form);
            model.addAttribute("planningFrom", form.getPlanningFrom());
            model.addAttribute("planningTo", form.getPlanningTo());
            model.addAttribute("calendarErrors", exception.getFieldErrors());
            return AVAILABILITY_VIEW;
        }
    }

    /**
     * Returns the calendar terms generated from the current, unsaved form so the
     * term list can be refreshed live. Does not persist anything.
     *
     * @param form current calendar form input
     * @param currentUser authenticated provider
     * @return preview calendar terms
     */
    @PostMapping("/provider/calendar/preview")
    @ResponseBody
    public List<CalendarTerm> previewCalendar(@ModelAttribute ProviderCalendarForm form,
                                              @CurrentUser User currentUser) {
        return providerCalendarService.previewCalendar(currentUser, form);
    }
}
