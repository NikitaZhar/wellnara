package life.wellnara.service;

import life.wellnara.dto.AppointmentView;
import life.wellnara.model.Appointment;
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

    /**
     * Builds an {@link AppointmentView} for the given display timezone.
     *
     * @param appointment appointment to project
     * @param displayZone timezone of the user who will see this view
     * @return populated view model
     */
    public AppointmentView toView(Appointment appointment, ZoneId displayZone) {
        LocalDateTime local = appointment.getStartDateTimeUtc()
                .atZone(ZoneOffset.UTC)
                .withZoneSameInstant(displayZone)
                .toLocalDateTime();

        return new AppointmentView(
                appointment.getId(),
                appointment.getClient().getUsername(),
                appointment.getOffering().getName(),
                local.toLocalDate(),
                local.toLocalTime(),
                appointment.getStatus(),
                appointment.getRejectionReason()
        );
    }
}