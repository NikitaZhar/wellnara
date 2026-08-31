package life.wellnara.controller;

import life.wellnara.exception.LocalizedException;
import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.web.CurrentUser;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Controller for provider appointment actions.
 */
@Controller
public class ProviderAppointmentController {

    private static final String APPOINTMENTS_VIEW = "provider-appointments";
    private static final String APPOINTMENTS_REDIRECT = "redirect:/provider/appointments";
    private static final String REQUESTS_VIEW = "provider-requests";
    private static final String REQUESTS_REDIRECT = "redirect:/provider/requests";

    private final AppointmentService appointmentService;
    private final ProviderPageModelAssembler providerPageModelAssembler;
    private final MessageSource messageSource;

    /**
     * Creates provider appointment controller.
     *
     * @param appointmentService service for appointment operations
     * @param providerPageModelAssembler assembler for provider page model
     * @param messageSource resolver of localized user-facing messages
     */
    public ProviderAppointmentController(AppointmentService appointmentService,
                                         ProviderPageModelAssembler providerPageModelAssembler,
                                         MessageSource messageSource) {
        this.appointmentService = appointmentService;
        this.providerPageModelAssembler = providerPageModelAssembler;
        this.messageSource = messageSource;
    }

    /**
     * Accepts a client appointment request, scheduling it.
     *
     * @param appointmentId appointment identifier
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return redirect to the appointments page, or that page with an error
     */
    @PostMapping("/provider/appointments/{appointmentId}/accept")
    public String acceptAppointment(@PathVariable Long appointmentId,
                                    @CurrentUser User currentUser,
                                    Model model) {
        return executeRequestAction(
                currentUser,
                model,
                provider -> appointmentService.acceptAppointment(provider, appointmentId)
        );
    }

    /**
     * Rejects a client appointment request.
     *
     * @param appointmentId appointment identifier
     * @param rejectionReason reason shown to client
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return redirect to the appointments page, or that page with an error
     */
    @PostMapping("/provider/appointments/{appointmentId}/reject")
    public String rejectAppointment(@PathVariable Long appointmentId,
                                    @RequestParam String rejectionReason,
                                    @CurrentUser User currentUser,
                                    Model model) {
        return executeRequestAction(
                currentUser,
                model,
                provider -> appointmentService.rejectAppointment(
                        provider,
                        appointmentId,
                        rejectionReason
                )
        );
    }

    /**
     * Cancels a scheduled appointment by the provider.
     *
     * <p>The provider may include a message shown to the client. When
     * {@code blockSlot} is set the freed time is blocked, so the client cannot
     * re-book the same slot and must choose another time; otherwise the slot
     * stays open for re-booking. This single action replaces the former separate
     * "cancel" and "reschedule" operations.
     *
     * @param appointmentId   appointment identifier
     * @param providerMessage optional message shown to the client
     * @param blockSlot       whether to block the freed time slot
     * @param currentUser     authenticated provider
     * @param model           MVC model
     * @return redirect to the appointments page, or that page with an error
     */
    @PostMapping("/provider/appointments/{appointmentId}/cancel")
    public String cancelScheduledAppointment(@PathVariable Long appointmentId,
                                             @RequestParam(required = false) String providerMessage,
                                             @RequestParam(required = false, defaultValue = "false") boolean blockSlot,
                                             @CurrentUser User currentUser,
                                             Model model) {
        return executeUpcomingAction(
                currentUser,
                model,
                provider -> appointmentService.cancelScheduledAppointment(
                        provider,
                        appointmentId,
                        providerMessage,
                        blockSlot
                )
        );
    }

    /**
     * Completes a scheduled appointment.
     *
     * @param appointmentId appointment identifier
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return redirect to the appointments page, or that page with an error
     */
    @PostMapping("/provider/appointments/{appointmentId}/complete")
    public String completeScheduledAppointment(@PathVariable Long appointmentId,
                                               @CurrentUser User currentUser,
                                               Model model) {
        return executeUpcomingAction(
                currentUser,
                model,
                provider -> appointmentService.completeScheduledAppointment(provider, appointmentId)
        );
    }

    /**
     * Marks a scheduled appointment as a no-show.
     *
     * @param appointmentId appointment identifier
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return redirect to the appointments page, or that page with an error
     */
    @PostMapping("/provider/appointments/{appointmentId}/no-show")
    public String markAppointmentNoShow(@PathVariable Long appointmentId,
                                        @CurrentUser User currentUser,
                                        Model model) {
        return executeUpcomingAction(
                currentUser,
                model,
                provider -> appointmentService.markAppointmentNoShow(provider, appointmentId)
        );
    }

    /**
     * Acknowledges provider appointment notification and removes it.
     *
     * @param appointmentId appointment identifier
     * @param currentUser authenticated provider
     * @param model MVC model
     * @return redirect to the appointments page, or that page with an error
     */
    @PostMapping("/provider/appointments/{appointmentId}/acknowledge")
    public String acknowledgeAppointmentNotification(@PathVariable Long appointmentId,
                                                     @CurrentUser User currentUser,
                                                     Model model) {
        return executeUpcomingAction(
                currentUser,
                model,
                provider -> appointmentService.acknowledgeProviderAppointmentNotification(
                        provider,
                        appointmentId
                )
        );
    }

    /**
     * Runs a request-list action (accept / reject) and keeps the provider on the
     * requests page, re-rendering it with the error on failure.
     */
    private String executeRequestAction(User currentUser, Model model, ProviderAppointmentAction action) {
        return executeAppointmentAction(currentUser, model, action, REQUESTS_REDIRECT, REQUESTS_VIEW);
    }

    /**
     * Runs an upcoming-list action (cancel / complete / no-show / acknowledge) and
     * keeps the provider on the upcoming page, re-rendering it with the error on failure.
     */
    private String executeUpcomingAction(User currentUser, Model model, ProviderAppointmentAction action) {
        return executeAppointmentAction(currentUser, model, action, APPOINTMENTS_REDIRECT, APPOINTMENTS_VIEW);
    }

    private String executeAppointmentAction(User currentUser,
                                            Model model,
                                            ProviderAppointmentAction action,
                                            String redirect,
                                            String errorView) {
        try {
            action.execute(currentUser);
            return redirect;
        } catch (IllegalArgumentException exception) {
            providerPageModelAssembler.populateAppointments(model, currentUser);
            model.addAttribute("appointmentActionError", LocalizedException.resolve(exception, messageSource, LocaleContextHolder.getLocale()));
            return errorView;
        }
    }

    /**
     * Provider appointment action callback.
     */
    @FunctionalInterface
    private interface ProviderAppointmentAction {

        /**
         * Executes appointment action for authenticated provider.
         *
         * @param provider authenticated provider
         */
        void execute(User provider);
    }
}
