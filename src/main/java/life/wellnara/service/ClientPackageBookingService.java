package life.wellnara.service;

import life.wellnara.exception.LocalizedException;
import life.wellnara.model.Appointment;
import life.wellnara.model.Offering;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.ServicePackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * Coordinates the client's one-step package purchase with the booking of its
 * first session.
 *
 * <p>A client buys N sessions of an offering and picks the time of the first one
 * in a single request. This service is the seam that composes the two domains
 * without either knowing about the other:
 * <ul>
 *   <li>{@link #requestPackage} validates the chosen slot is still bookable, then
 *       delegates to {@link WalletCommandService} to reserve the whole price and
 *       record the requested package (with the first-session start stored on it);</li>
 *   <li>{@link #acceptPackageRequest} activates the package through
 *       {@link WalletCommandService} (money settled, sessions granted) and then
 *       schedules the first session through {@link AppointmentCommandService},
 *       consuming one of the freshly granted sessions.</li>
 * </ul>
 *
 * <p>Each public method runs in one transaction, so the package and its first
 * appointment always commit or roll back together — the provider never ends up
 * with an approved package whose first session could not be booked.
 */
@Service
public class ClientPackageBookingService {

    private final WalletCommandService walletCommandService;
    private final AppointmentCommandService appointmentCommandService;
    private final AppointmentAvailabilityService availabilityService;
    private final ClientOfferingService clientOfferingService;
    private final ServicePackageRepository servicePackageRepository;
    private final ProviderClientLinkRepository providerClientLinkRepository;

    /**
     * Creates the client package booking service.
     *
     * @param walletCommandService        service that reserves and activates the package
     * @param appointmentCommandService   service that requests and schedules appointments
     * @param availabilityService         service that lists bookable times for a day
     * @param clientOfferingService       service for client access to provider offerings
     * @param servicePackageRepository    repository for service packages
     * @param providerClientLinkRepository repository for provider-client links (view-only guard)
     */
    public ClientPackageBookingService(WalletCommandService walletCommandService,
                                       AppointmentCommandService appointmentCommandService,
                                       AppointmentAvailabilityService availabilityService,
                                       ClientOfferingService clientOfferingService,
                                       ServicePackageRepository servicePackageRepository,
                                       ProviderClientLinkRepository providerClientLinkRepository) {
        this.walletCommandService = walletCommandService;
        this.appointmentCommandService = appointmentCommandService;
        this.availabilityService = availabilityService;
        this.clientOfferingService = clientOfferingService;
        this.servicePackageRepository = servicePackageRepository;
        this.providerClientLinkRepository = providerClientLinkRepository;
    }

    /**
     * Requests a package of {@code sessions} sessions of the offering and stores
     * the chosen start of its first session. The whole price is held on the
     * client's wallet; nothing is booked yet.
     *
     * @param client       authenticated client
     * @param offeringId   offering the package is for
     * @param sessions     number of sessions (1 up to the offering's maximum)
     * @param selectedDate first-session date in the provider timezone (for the availability check)
     * @param selectedTime first-session time in the provider timezone (for the availability check)
     * @param startUtc     the same first-session start converted to UTC (stored on the package)
     * @throws IllegalArgumentException if the slot is no longer bookable, the count
     *                                  is out of range, or the wallet lacks the funds
     */
    @Transactional
    public void requestPackage(User client,
                               Long offeringId,
                               int sessions,
                               LocalDate selectedDate,
                               LocalTime selectedTime,
                               LocalDateTime startUtc) {
        requireActiveClient(client);
        User provider = clientOfferingService.getProviderOfClient(client);
        Offering offering = clientOfferingService.getOfferingOfClientProvider(client, offeringId);

        if (!availabilityService.getBookableTimes(provider, offering, selectedDate).contains(selectedTime)) {
            throw new LocalizedException("error.package.slotGone",
                    "That time is no longer available. Pick another slot.");
        }

        walletCommandService.requestPackage(client, offeringId, sessions, startUtc, null);
    }

    /**
     * Withdraws a client's own pending package request: the held price is released
     * back to the wallet. Nothing was booked yet, so there is nothing else to undo.
     *
     * @param client    authenticated client (must own the package)
     * @param packageId requested package identifier
     * @throws IllegalArgumentException if the package is not found or not the client's
     * @throws IllegalStateException    if the package is not awaiting approval
     */
    @Transactional
    public void cancelPackageRequest(User client, Long packageId) {
        walletCommandService.cancelPackageRequestByClient(client, packageId);
    }

    /**
     * Guards that the client still has full (booking) access. A client the provider
     * has made inactive may sign in and view services, but cannot request packages.
     *
     * @param client authenticated client
     * @throws IllegalArgumentException if the client is not linked, or is view-only
     */
    private void requireActiveClient(User client) {
        ProviderClientLink link = providerClientLinkRepository.findByClient(client)
                .orElseThrow(() -> new LocalizedException(
                        "error.clientOffering.providerLinkNotFound", "Provider link not found"));
        if (!link.isActive()) {
            throw new LocalizedException("error.client.inactiveViewOnly",
                    "Your access is view-only; ask your provider to reactivate you to book.");
        }
    }

    /**
     * Approves a client's package request: activates the package (its held price is
     * settled and the sessions are granted) and schedules its first session from
     * the start the client chose, consuming one of the granted sessions.
     *
     * @param provider  provider approving the request (must own the package)
     * @param packageId requested package identifier
     * @throws IllegalArgumentException if the package is not the provider's or the
     *                                  chosen first-session slot is no longer free
     * @throws IllegalStateException    if the package is not awaiting approval
     */
    @Transactional
    public void acceptPackageRequest(User provider, Long packageId) {
        walletCommandService.acceptPackageRequest(provider, packageId);

        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found"));
        LocalDateTime firstSessionStartUtc = servicePackage.getFirstSessionStartUtc();
        if (firstSessionStartUtc == null) {
            return;
        }

        User client = servicePackage.getWallet().getClient();
        Appointment firstSession = appointmentCommandService.requestAppointment(
                client, provider.getId(), servicePackage.getOffering().getId(), firstSessionStartUtc);
        appointmentCommandService.acceptAppointment(provider, firstSession.getId());
    }
}
