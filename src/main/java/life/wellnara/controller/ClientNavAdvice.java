package life.wellnara.controller;

import life.wellnara.dto.AppointmentView;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.User;
import life.wellnara.service.AppointmentService;
import life.wellnara.service.WalletQueryService;
import life.wellnara.web.CurrentUser;

import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

/**
 * Supplies the client section-navigation badge counts to every {@link ClientController}
 * page, so the counts are consistent wherever the nav is shown without each page
 * computing them.
 *
 * <p>Two counts: {@code navNoticeCount} — provider cancellations awaiting the
 * client's attention (shown as an attention badge on the appointments tab) — and
 * {@code navPendingCount} — requests still awaiting the provider (a plain counter
 * on the requests tab). Scoped to {@link ClientController}: the nav fragment is
 * only rendered on those pages.
 */
@ControllerAdvice(assignableTypes = ClientController.class)
public class ClientNavAdvice {

    private final AppointmentService appointmentService;
    private final WalletQueryService walletQueryService;

    /**
     * Creates the client navigation advice.
     *
     * @param appointmentService service for appointment queries
     * @param walletQueryService service for pending package requests
     */
    public ClientNavAdvice(AppointmentService appointmentService,
                           WalletQueryService walletQueryService) {
        this.appointmentService = appointmentService;
        this.walletQueryService = walletQueryService;
    }

    /**
     * Adds the navigation badge counts for the current client to the model.
     *
     * @param client authenticated client
     * @param model  MVC model
     */
    @ModelAttribute
    public void addNavigationBadges(@CurrentUser User client, Model model) {
        if (client == null) {
            return;
        }

        List<AppointmentView> views = appointmentService.getAppointmentViewsOfClient(client);
        long notices = views.stream()
                .filter(view -> view.getStatus() == AppointmentStatus.CANCELLED)
                .count();
        long pendingAppointments = views.stream()
                .filter(view -> view.getStatus() == AppointmentStatus.REQUESTED)
                .count();
        int pendingPackages = walletQueryService.getPendingPackageRequestsOfClient(client).size();

        model.addAttribute("navNoticeCount", notices);
        model.addAttribute("navPendingCount", pendingAppointments + pendingPackages);
    }
}
