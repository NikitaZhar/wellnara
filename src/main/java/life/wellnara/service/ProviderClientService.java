package life.wellnara.service;

import life.wellnara.exception.LocalizedException;
import life.wellnara.model.AppointmentStatus;
import life.wellnara.model.PackageStatus;
import life.wellnara.model.ProviderClientLink;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.ServicePackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Service for provider client operations.
 */
@Service
public class ProviderClientService {

    /** Appointment statuses that count as an open commitment for a client. */
    private static final Set<AppointmentStatus> OPEN_APPOINTMENT_STATUSES =
            Set.of(AppointmentStatus.REQUESTED, AppointmentStatus.SCHEDULED);

    /** Package statuses that count as an open commitment for a client. */
    private static final Set<PackageStatus> OPEN_PACKAGE_STATUSES =
            Set.of(PackageStatus.REQUESTED, PackageStatus.ACTIVE);

    private final ProviderClientLinkRepository providerClientLinkRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServicePackageRepository servicePackageRepository;

    /**
     * Creates provider client service.
     *
     * @param providerClientLinkRepository repository for provider-client links
     * @param appointmentRepository        repository for appointments (open-commitment check)
     * @param servicePackageRepository     repository for packages (open-commitment check)
     */
    public ProviderClientService(ProviderClientLinkRepository providerClientLinkRepository,
                                 AppointmentRepository appointmentRepository,
                                 ServicePackageRepository servicePackageRepository) {
        this.providerClientLinkRepository = providerClientLinkRepository;
        this.appointmentRepository = appointmentRepository;
        this.servicePackageRepository = servicePackageRepository;
    }

    /**
     * Returns all clients of provider.
     *
     * @param provider provider user
     * @return list of provider-client links
     */
    @Transactional(readOnly = true)
    public List<ProviderClientLink> getClientsOfProvider(User provider) {
        validateProvider(provider);
        return providerClientLinkRepository.findAllByProvider(provider);
    }

    /**
     * Returns the provider's link to one of their clients.
     *
     * @param provider provider user
     * @param clientId client identifier
     * @return the provider-client link
     * @throws IllegalArgumentException if the user is not a provider or the client is not linked
     */
    @Transactional(readOnly = true)
    public ProviderClientLink getClientLink(User provider, Long clientId) {
        validateProvider(provider);
        return providerClientLinkRepository.findByProviderAndClientId(provider, clientId)
                .orElseThrow(() -> new LocalizedException("error.client.notLinked", "Client is not linked to provider"));
    }

    /**
     * Activates or deactivates one of the provider's clients. Reactivation is always
     * allowed; deactivation is refused while the client still has open commitments
     * (a requested or scheduled appointment, or a requested or active package), since
     * those must be resolved first.
     *
     * @param provider provider user
     * @param clientId client identifier
     * @param active   {@code true} to activate, {@code false} to deactivate
     * @throws IllegalArgumentException if the client is not linked, or deactivation is
     *                                  requested while the client has open commitments
     */
    @Transactional
    public void setClientActive(User provider, Long clientId, boolean active) {
        ProviderClientLink link = getClientLink(provider, clientId);
        if (active) {
            link.activate();
            return;
        }
        if (hasOpenCommitments(provider, link.getClient())) {
            throw new LocalizedException("error.client.hasOpenCommitments",
                    "This client still has appointments or package requests and cannot be made inactive");
        }
        link.deactivate();
    }

    /**
     * Whether a client can be deactivated right now — i.e. has no open commitments.
     * Drives the enabled state of the deactivate action on the client page.
     *
     * @param provider provider user
     * @param client   the client
     * @return {@code true} if the client has no open appointment or package
     */
    @Transactional(readOnly = true)
    public boolean isDeactivatable(User provider, User client) {
        return !hasOpenCommitments(provider, client);
    }

    private boolean hasOpenCommitments(User provider, User client) {
        return appointmentRepository.existsByClientAndProviderAndStatusIn(client, provider, OPEN_APPOINTMENT_STATUSES)
                || servicePackageRepository.existsByWallet_ClientAndWallet_ProviderAndStatusIn(client, provider, OPEN_PACKAGE_STATUSES);
    }

    /**
     * Validates provider role.
     *
     * @param provider provider user
     */
    private void validateProvider(User provider) {
        if (provider.getRole() != UserRole.PROVIDER) {
            throw new IllegalArgumentException("Only provider can manage clients");
        }
    }
}
