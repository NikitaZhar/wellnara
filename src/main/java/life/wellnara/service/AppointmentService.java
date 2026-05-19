package life.wellnara.service;

import life.wellnara.dto.AppointmentView;
import life.wellnara.dto.BookableDateOption;
import life.wellnara.dto.CalendarTerm;
import life.wellnara.model.Appointment;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * Backward-compatibility facade over {@link AppointmentCommandService},
 * {@link AppointmentQueryService}, and {@link AppointmentAvailabilityService}.
 *
 * <p>Exists solely so that existing controllers compile without change.
 * Every method is a one-line delegate.
 * <strong>No logic lives here.</strong>
 *
 * <p>Migration: once controllers inject the sub-services directly,
 * this class should be deleted.
 */
@Service
public class AppointmentService {

    private final AppointmentCommandService commandService;
    private final AppointmentQueryService queryService;
    private final AppointmentAvailabilityService availabilityService;

    /**
     * Creates appointment service facade.
     *
     * @param commandService      service for state-changing appointment operations
     * @param queryService        service for read-only appointment queries
     * @param availabilityService service for slot availability computation
     */
    public AppointmentService(AppointmentCommandService commandService,
                               AppointmentQueryService queryService,
                               AppointmentAvailabilityService availabilityService) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.availabilityService = availabilityService;
    }

    // ===== Commands =====

    public Appointment requestAppointment(User client, Long providerId,
                                          Long offeringId, LocalDateTime startDateTimeUtc) {
        return commandService.requestAppointment(client, providerId, offeringId, startDateTimeUtc);
    }

    public void rejectAppointment(User provider, Long appointmentId, String rejectionReason) {
        commandService.rejectAppointment(provider, appointmentId, rejectionReason);
    }

    public void requestPaymentForAppointment(User provider, Long appointmentId) {
        commandService.requestPaymentForAppointment(provider, appointmentId);
    }

    public void cancelConfirmedAppointment(User provider, Long appointmentId) {
        commandService.cancelConfirmedAppointment(provider, appointmentId);
    }

    public void rescheduleConfirmedAppointment(User provider,
                                               Long appointmentId,
                                               String providerMessage) {
        commandService.rescheduleConfirmedAppointment(provider, appointmentId, providerMessage);
    }

    public void completeConfirmedAppointment(User provider, Long appointmentId) {
        commandService.completeConfirmedAppointment(provider, appointmentId);
    }

    public void acknowledgeProviderAppointmentNotification(User provider, Long appointmentId) {
        commandService.acknowledgeProviderAppointmentNotification(provider, appointmentId);
    }

    public void payForAppointment(User client, Long appointmentId) {
        commandService.payForAppointment(client, appointmentId);
    }

    public void cancelPendingAppointmentByClient(User client, Long appointmentId) {
        commandService.cancelPendingAppointmentByClient(client, appointmentId);
    }

    public void cancelConfirmedAppointmentByClient(User client, Long appointmentId) {
        commandService.cancelConfirmedAppointmentByClient(client, appointmentId);
    }

    public void acknowledgeRejectedAppointment(User client, Long appointmentId) {
        commandService.acknowledgeRejectedAppointment(client, appointmentId);
    }

    public void deleteExpiredUnpaidAppointments() {
        commandService.deleteExpiredUnpaidAppointments();
    }

    // ===== Queries =====

    public List<Appointment> getAppointmentsOfClient(User client) {
        return queryService.getAppointmentsOfClient(client);
    }

    public List<Appointment> getAppointmentsOfProvider(User provider) {
        return queryService.getAppointmentsOfProvider(provider);
    }

    public List<AppointmentView> getAppointmentViewsOfClient(User client) {
        return queryService.getAppointmentViewsOfClient(client);
    }

    public List<AppointmentView> getAppointmentViewsOfProvider(User provider) {
        return queryService.getAppointmentViewsOfProvider(provider);
    }

    public List<AppointmentView> getConfirmedAppointmentViewsOfProvider(User provider) {
        return queryService.getConfirmedAppointmentViewsOfProvider(provider);
    }

    public List<AppointmentView> getConfirmedAppointmentViewsOfClient(User client) {
        return queryService.getConfirmedAppointmentViewsOfClient(client);
    }

    public List<AppointmentView> getAppointmentNotificationViewsOfProvider(User provider) {
        return queryService.getAppointmentNotificationViewsOfProvider(provider);
    }

    // ===== Availability =====

    public List<CalendarTerm> getFreeCalendarTerms(User provider) {
        return availabilityService.getFreeCalendarTerms(provider);
    }

    public List<BookableDateOption> getBookableDateOptions(User provider, Offering offering) {
        return availabilityService.getBookableDateOptions(provider, offering);
    }

    public List<LocalTime> getBookableTimes(User provider, Offering offering, LocalDate date) {
        return availabilityService.getBookableTimes(provider, offering, date);
    }

    public List<LocalTime> getBookableTimesForTerm(CalendarTerm term, Offering offering) {
        return availabilityService.getBookableTimesForTerm(term, offering);
    }
}