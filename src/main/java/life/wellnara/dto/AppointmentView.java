package life.wellnara.dto;

import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.CancellationInitiator;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * View model for displaying appointment data on UI pages.
 */
public class AppointmentView {

	private final Long id;
	private final String clientName;
	private final String offeringName;
	private final LocalDate localDate;
	private final LocalTime localTime;
	private final AppointmentStatus status;
	private final CancellationInitiator cancellationInitiator;
	private final String rejectionReason;
	private boolean completable;

	public AppointmentView(Long id,
			String clientName,
			String offeringName,
			LocalDate localDate,
			LocalTime localTime,
			AppointmentStatus status,
			CancellationInitiator cancellationInitiator,
			String rejectionReason) {
		this.id = id;
		this.clientName = clientName;
		this.offeringName = offeringName;
		this.localDate = localDate;
		this.localTime = localTime;
		this.status = status;
		this.cancellationInitiator = cancellationInitiator;
		this.rejectionReason = rejectionReason;
	}

	public Long getId() {
		return id;
	}

	public String getClientName() {
		return clientName;
	}

	public String getRejectionReason() {
	    return rejectionReason;
	}

	public String getOfferingName() {
		return offeringName;
	}

	public LocalDate getLocalDate() {
		return localDate;
	}

	public LocalTime getLocalTime() {
		return localTime;
	}

	public AppointmentStatus getStatus() {
		return status;
	}

	public CancellationInitiator getCancellationInitiator() {
		return cancellationInitiator;
	}

	public boolean isCompletable() {
		return completable;
	}

	public void setCompletable(boolean completable) {
		this.completable = completable;
	}
}
