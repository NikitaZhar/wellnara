package life.wellnara.service;

import life.wellnara.dto.AppointmentView;
import life.wellnara.model.Appointment;
import life.wellnara.service.time.ApplicationTimeService;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Converts {@link Appointment} entities into {@link AppointmentView} DTOs.
 *
 * <p>Single responsibility: project appointment fields into a view model,
 * converting the UTC start time to the caller-supplied display timezone.
 *
 * <p>Stateless. No business logic. No repository access.
 */
@Component
public class AppointmentViewMapper {

	private final ApplicationTimeService applicationTimeService;

	public AppointmentViewMapper(ApplicationTimeService applicationTimeService) {
	    this.applicationTimeService = applicationTimeService;
	}

    /**
     * Builds an {@link AppointmentView} for the given display timezone.
     *
     * @param appointment appointment to project
     * @param clientName  resolved client display name
     * @param displayZone timezone of the user who will see this view
     * @return populated view model
     */
	public AppointmentView toView(Appointment appointment, String clientName, ZoneId displayZone) {
	    LocalDateTime local = appointment.getStartDateTimeUtc()
	            .atZone(ZoneOffset.UTC)
	            .withZoneSameInstant(displayZone)
	            .toLocalDateTime();

	    AppointmentView view = new AppointmentView(
	            appointment.getId(),
	            appointment.getOffering().getId(),
	            clientName,
	            appointment.getOffering().getName(),
	            local.toLocalDate(),
	            local.toLocalTime(),
	            appointment.getStatus(),
	            appointment.getCancellationInitiator(),
	            appointment.getRejectionReason()
	    );

	    // The appointment start in the display timezone marks the boundary between the
	    // two action windows: before it the provider may cancel; from it on the
	    // provider records the outcome (Completed / No-show). There is no window in
	    // which both are offered.
	    LocalDateTime now = applicationTimeService.currentDateTime(displayZone);
	    boolean started = !now.isBefore(local);
	    view.setCompletable(started);

	    return view;
	}
}
