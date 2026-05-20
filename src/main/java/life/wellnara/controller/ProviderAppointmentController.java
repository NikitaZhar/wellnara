package life.wellnara.controller;

import jakarta.servlet.http.HttpSession;
import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.SessionUserService;
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

    private static final String LOGIN_REDIRECT = "redirect:/auth/login";
    private static final String PROVIDER_VIEW = "provider";
    private static final String PROVIDER_CALENDAR_REDIRECT = "redirect:/provider?section=provider-calendar";

    private final AppointmentService appointmentService;
    private final SessionUserService sessionUserService;
    private final ProviderPageModelAssembler providerPageModelAssembler;

    /**
     * Creates provider appointment controller.
     *
     * @param appointmentService service for appointment operations
     * @param sessionUserService service for authenticated session user access
     * @param providerPageModelAssembler assembler for provider page model
     */
    public ProviderAppointmentController(AppointmentService appointmentService,
                                         SessionUserService sessionUserService,
                                         ProviderPageModelAssembler providerPageModelAssembler) {
        this.appointmentService = appointmentService;
        this.sessionUserService = sessionUserService;
        this.providerPageModelAssembler = providerPageModelAssembler;
    }

    /**
     * Accepts appointment request and asks client for payment.
     *
     * @param appointmentId appointment identifier
     * @param session current HTTP session
     * @param model MVC model
     * @return redirect to provider calendar section or provider page with error
     */
    @PostMapping("/provider/appointments/{appointmentId}/request-payment")
    public String requestPaymentForAppointment(@PathVariable Long appointmentId,
                                               HttpSession session,
                                               Model model) {
        return executeAppointmentAction(
                session,
                model,
                provider -> appointmentService.requestPaymentForAppointment(provider, appointmentId)
        );
    }

    /**
     * Rejects client appointment request.
     *
     * @param appointmentId appointment identifier
     * @param rejectionReason reason shown to client
     * @param session current HTTP session
     * @param model MVC model
     * @return redirect to provider calendar section or provider page with error
     */
    @PostMapping("/provider/appointments/{appointmentId}/reject")
    public String rejectAppointment(@PathVariable Long appointmentId,
                                    @RequestParam String rejectionReason,
                                    HttpSession session,
                                    Model model) {
        return executeAppointmentAction(
                session,
                model,
                provider -> appointmentService.rejectAppointment(
                        provider,
                        appointmentId,
                        rejectionReason
                )
        );
    }

    /**
     * Reschedules confirmed appointment by cancelling it and asking client to choose another time.
     *
     * @param appointmentId appointment identifier
     * @param providerMessage message shown to client
     * @param session current HTTP session
     * @param model MVC model
     * @return redirect to provider calendar section or provider page with error
     */
    @PostMapping("/provider/appointments/{appointmentId}/reschedule")
    public String rescheduleConfirmedAppointment(@PathVariable Long appointmentId,
                                                 @RequestParam String providerMessage,
                                                 HttpSession session,
                                                 Model model) {
        return executeAppointmentAction(
                session,
                model,
                provider -> appointmentService.rescheduleConfirmedAppointment(
                        provider,
                        appointmentId,
                        providerMessage
                )
        );
    }

    /**
     * Cancels confirmed appointment by provider.
     *
     * @param appointmentId appointment identifier
     * @param session current HTTP session
     * @param model MVC model
     * @return redirect to provider calendar section or provider page with error
     */
    @PostMapping("/provider/appointments/{appointmentId}/cancel")
    public String cancelConfirmedAppointment(@PathVariable Long appointmentId,
                                             HttpSession session,
                                             Model model) {
        return executeAppointmentAction(
                session,
                model,
                provider -> appointmentService.cancelConfirmedAppointment(provider, appointmentId)
        );
    }

    /**
     * Completes confirmed appointment.
     *
     * @param appointmentId appointment identifier
     * @param session current HTTP session
     * @param model MVC model
     * @return redirect to provider calendar section or provider page with error
     */
    @PostMapping("/provider/appointments/{appointmentId}/complete")
    public String completeConfirmedAppointment(@PathVariable Long appointmentId,
                                               HttpSession session,
                                               Model model) {
        return executeAppointmentAction(
                session,
                model,
                provider -> appointmentService.completeConfirmedAppointment(provider, appointmentId)
        );
    }

    /**
     * Acknowledges provider appointment notification and removes it.
     *
     * @param appointmentId appointment identifier
     * @param session current HTTP session
     * @param model MVC model
     * @return redirect to provider calendar section or provider page with error
     */
    @PostMapping("/provider/appointments/{appointmentId}/acknowledge")
    public String acknowledgeAppointmentNotification(@PathVariable Long appointmentId,
                                                     HttpSession session,
                                                     Model model) {
        return executeAppointmentAction(
                session,
                model,
                provider -> appointmentService.acknowledgeProviderAppointmentNotification(
                        provider,
                        appointmentId
                )
        );
    }

    private String executeAppointmentAction(HttpSession session,
                                            Model model,
                                            ProviderAppointmentAction action) {
        User currentUser = sessionUserService.requireProvider(session);

        if (currentUser == null) {
            return LOGIN_REDIRECT;
        }

        try {
            action.execute(currentUser);
            return PROVIDER_CALENDAR_REDIRECT;
        } catch (IllegalArgumentException exception) {
            providerPageModelAssembler.populate(model, currentUser);
            model.addAttribute("appointmentActionError", exception.getMessage());
            return PROVIDER_VIEW;
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