package life.wellnara.service;

import life.wellnara.dto.PackagePricing;
import life.wellnara.exception.LocalizedException;
import life.wellnara.model.Offering;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.AppointmentRepository;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ServicePackageRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service for offering management.
 */
@Service
public class OfferingService {

    private final OfferingRepository offeringRepository;
    private final AppointmentRepository appointmentRepository;
    private final ServicePackageRepository servicePackageRepository;

    /**
     * Creates offering service.
     *
     * @param offeringRepository        repository for offerings
     * @param appointmentRepository     repository for appointments (delete guard)
     * @param servicePackageRepository  repository for packages (delete guard)
     */
    public OfferingService(OfferingRepository offeringRepository,
                           AppointmentRepository appointmentRepository,
                           ServicePackageRepository servicePackageRepository) {
        this.offeringRepository = offeringRepository;
        this.appointmentRepository = appointmentRepository;
        this.servicePackageRepository = servicePackageRepository;
    }

    /**
     * Creates an offering for the provider. The offering price inherits the
     * provider's wallet currency, which must already be set.
     *
     * @param provider        provider owner
     * @param name            offering name
     * @param description     offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     * @param prepMinutes     provider-only buffer before the session (minutes, >= 0)
     * @param wrapMinutes     provider-only buffer after the session (minutes, >= 0)
     * @param packagePricing  optional package pricing (never {@code null}; use {@link PackagePricing#none()})
     * @throws IllegalArgumentException if the user is not a provider, has no
     *                                  currency set, a buffer is negative, or the
     *                                  package pricing is invalid
     */
    @Transactional
    public void createOffering(User provider,
                               String name,
                               String description,
                               BigDecimal pricePerSession,
                               Integer durationMinutes,
                               int prepMinutes,
                               int wrapMinutes,
                               PackagePricing packagePricing) {
        validateProvider(provider);
        String currency = requireProviderCurrency(provider);
        requireNonNegativeBuffers(prepMinutes, wrapMinutes);

        Offering offering = new Offering(
                provider,
                name,
                description,
                pricePerSession,
                durationMinutes,
                prepMinutes,
                wrapMinutes
        );
        offering.setCurrency(currency);
        applyPackagePricing(offering, packagePricing);

        offeringRepository.save(offering);
    }

    /**
     * Returns all offerings of provider.
     *
     * @param provider provider owner
     * @return list of offerings
     */
    @Transactional(readOnly = true)
    public List<Offering> getOfferingsOfProvider(User provider) {
        validateProvider(provider);
        return offeringRepository.findAllByProvider(provider);
    }

    /**
     * Returns offering owned by provider.
     *
     * @param provider provider owner
     * @param offeringId offering identifier
     * @return offering owned by provider
     */
    @Transactional(readOnly = true)
    public Offering getOfferingOfProvider(User provider, Long offeringId) {
        validateProvider(provider);
        return getOwnedOffering(provider, offeringId);
    }

    /**
     * Updates offering owned by provider. Currency is not editable per offering —
     * it follows the provider currency set at registration.
     *
     * @param provider provider owner
     * @param offeringId offering identifier
     * @param name offering name
     * @param description offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     */
    /**
     * Updates an offering owned by the provider, including the provider-only
     * prep/wrap buffers and the optional package pricing. Currency is not
     * editable per offering.
     *
     * @param provider        provider owner
     * @param offeringId      offering identifier
     * @param name            offering name
     * @param description     offering description
     * @param pricePerSession price per session
     * @param durationMinutes session duration in minutes
     * @param prepMinutes     buffer before the session (minutes, >= 0)
     * @param wrapMinutes     buffer after the session (minutes, >= 0)
     * @param packagePricing  optional package pricing (never {@code null}; use {@link PackagePricing#none()})
     */
    @Transactional
    public void updateOffering(User provider,
                               Long offeringId,
                               String name,
                               String description,
                               BigDecimal pricePerSession,
                               Integer durationMinutes,
                               int prepMinutes,
                               int wrapMinutes,
                               PackagePricing packagePricing) {
        validateProvider(provider);
        requireNonNegativeBuffers(prepMinutes, wrapMinutes);

        Offering offering = getOwnedOffering(provider, offeringId);
        offering.setName(name);
        offering.setDescription(description);
        offering.setPricePerSession(pricePerSession);
        offering.setDurationMinutes(durationMinutes);
        offering.setPrepMinutes(prepMinutes);
        offering.setWrapMinutes(wrapMinutes);
        applyPackagePricing(offering, packagePricing);
    }

    /**
     * Activates or deactivates an offering owned by the provider. A deactivated
     * offering is hidden from clients and cannot be booked, while its existing
     * appointments and history are kept.
     *
     * @param provider   provider owner
     * @param offeringId offering identifier
     * @param active     {@code true} to activate, {@code false} to deactivate
     * @throws IllegalArgumentException if the user is not a provider or does not own the offering
     */
    @Transactional
    public void setOfferingActive(User provider, Long offeringId, boolean active) {
        validateProvider(provider);
        Offering offering = getOwnedOffering(provider, offeringId);
        if (active) {
            offering.activate();
        } else {
            offering.deactivate();
        }
    }

    /**
     * Deletes an offering owned by the provider, but only when nothing references
     * it. An offering that has any appointment or any package cannot be deleted
     * ({@code offering_id} is a non-null foreign key on both) — it must be
     * deactivated instead.
     *
     * @param provider   provider owner
     * @param offeringId offering identifier
     * @throws IllegalArgumentException if the user is not a provider, does not own
     *                                  the offering, or the offering is still referenced
     */
    @Transactional
    public void deleteOffering(User provider, Long offeringId) {
        validateProvider(provider);
        Offering offering = getOwnedOffering(provider, offeringId);
        if (isReferenced(offering)) {
            throw new LocalizedException("error.offering.hasHistory",
                    "This service has appointments or packages and cannot be deleted; deactivate it instead");
        }
        offeringRepository.delete(offering);
    }

    /**
     * Whether the offering can be deleted — i.e. no appointment and no package
     * references it. Drives the enabled state of the delete action on the edit page.
     *
     * @param offering offering to check
     * @return {@code true} if the offering has no references and may be deleted
     */
    @Transactional(readOnly = true)
    public boolean isDeletable(Offering offering) {
        return !isReferenced(offering);
    }

    private boolean isReferenced(Offering offering) {
        return appointmentRepository.existsByOffering(offering)
                || servicePackageRepository.existsByOffering(offering);
    }

    /**
     * Guards that the provider-only preparation and wrap-up buffers are not
     * negative. Zero is allowed and means "no buffer".
     *
     * @param prepMinutes preparation buffer in minutes
     * @param wrapMinutes wrap-up buffer in minutes
     * @throws IllegalArgumentException if either buffer is negative
     */
    private void requireNonNegativeBuffers(int prepMinutes, int wrapMinutes) {
        if (prepMinutes < 0 || wrapMinutes < 0) {
            throw new LocalizedException("error.offering.prepWrapNegative", "Preparation and wrap-up time cannot be negative");
        }
    }

    /**
     * Validates and applies package pricing to the offering. When the pricing is
     * empty the service is marked as not sold as a package.
     *
     * <p>Rules when a package price is present: the price is positive and does not
     * exceed the single-session price (a package is never dearer per session), and
     * the maximum session count satisfies {@code 1 <= max <= }{@link Offering#PACKAGE_SESSIONS_CAP}.
     *
     * @param offering       offering to configure
     * @param packagePricing pricing entered on the form
     * @throws IllegalArgumentException if the pricing is invalid
     */
    private void applyPackagePricing(Offering offering, PackagePricing packagePricing) {
        if (!packagePricing.isPackageable()) {
            offering.setPackagePricePerSession(null);
            offering.setMaxPackageSessions(null);
            return;
        }

        BigDecimal packagePrice = packagePricing.pricePerSession();
        if (packagePrice.signum() <= 0) {
            throw new LocalizedException("error.offering.packagePricePositive", "Package price per session must be positive");
        }
        if (packagePrice.compareTo(offering.getPricePerSession()) > 0) {
            throw new LocalizedException("error.offering.packagePriceTooHigh", "Package price per session cannot exceed the single-session price");
        }

        Integer max = packagePricing.maxSessions();
        if (max != null && max < 1) {
            throw new LocalizedException("error.offering.maxSessionsMin", "Maximum package sessions must be at least 1");
        }
        if (max != null && max > Offering.PACKAGE_SESSIONS_CAP) {
            throw new LocalizedException("error.offering.maxSessionsCap", "Maximum package sessions cannot exceed " + Offering.PACKAGE_SESSIONS_CAP, Offering.PACKAGE_SESSIONS_CAP);
        }

        offering.setPackagePricePerSession(packagePrice);
        offering.setMaxPackageSessions(max);
    }

    /**
     * Returns offering owned by provider.
     *
     * @param provider provider owner
     * @param offeringId offering identifier
     * @return offering
     */
    private Offering getOwnedOffering(User provider, Long offeringId) {
        return offeringRepository.findByProviderAndId(provider, offeringId)
                .orElseThrow(() -> new LocalizedException("error.offering.notFound", "Offering not found"));
    }

    /**
     * Validates provider role.
     *
     * @param provider provider user
     */
    private void validateProvider(User provider) {
        if (provider.getRole() != UserRole.PROVIDER) {
            throw new LocalizedException("error.offering.onlyProvider", "Only provider can manage offerings");
        }
    }

    private String requireProviderCurrency(User provider) {
        String currency = provider.getCurrency();
        if (currency == null || currency.isBlank()) {
            throw new LocalizedException("error.offering.currencyNotSet", "Provider currency is not set");
        }
        return currency;
    }
}
