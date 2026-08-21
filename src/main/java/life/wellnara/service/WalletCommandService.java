package life.wellnara.service;

import life.wellnara.model.Offering;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.PackageStatus;
import life.wellnara.model.Wallet;
import life.wellnara.service.wallet.WalletLedgerCalculator;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.ServicePackageRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.time.ApplicationTimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Provider-only money-in operations on a client's wallet.
 *
 * <p>Two ways money enters a wallet: a manual {@link WalletEntryType#TOP_UP} for
 * cash the provider received outside the system, and a
 * {@link WalletEntryType#PACKAGE_GRANT} that grants a package of pre-paid
 * sessions. Both are append-only ledger writes; a client's wallet is created on
 * first use and inherits the provider currency.
 *
 * <p>Every operation re-checks the provider role and that the client belongs to
 * the provider on the service layer — the {@code /provider/**} route guard is
 * the first line, this is the second (defense in depth). The client role can
 * only read its wallet, never write it.
 */
@Service
public class WalletCommandService {

    private static final int MAX_MONEY_SCALE = 2;

    private final WalletRepository walletRepository;
    private final WalletEntryRepository walletEntryRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final UserRepository userRepository;
    private final OfferingRepository offeringRepository;
    private final ProviderClientLinkRepository providerClientLinkRepository;
    private final ApplicationTimeService applicationTimeService;
    private final WalletLedgerCalculator ledgerCalculator;

    /**
     * Creates wallet command service.
     *
     * @param walletRepository             repository for wallets
     * @param walletEntryRepository        repository for wallet ledger entries
     * @param servicePackageRepository     repository for service packages
     * @param userRepository               repository for users
     * @param offeringRepository           repository for offerings
     * @param providerClientLinkRepository repository for provider-client links
     * @param applicationTimeService       source of current application time (UTC)
     */
    public WalletCommandService(WalletRepository walletRepository,
                                WalletEntryRepository walletEntryRepository,
                                ServicePackageRepository servicePackageRepository,
                                UserRepository userRepository,
                                OfferingRepository offeringRepository,
                                ProviderClientLinkRepository providerClientLinkRepository,
                                ApplicationTimeService applicationTimeService,
                                WalletLedgerCalculator ledgerCalculator) {
        this.walletRepository = walletRepository;
        this.walletEntryRepository = walletEntryRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.userRepository = userRepository;
        this.offeringRepository = offeringRepository;
        this.providerClientLinkRepository = providerClientLinkRepository;
        this.applicationTimeService = applicationTimeService;
        this.ledgerCalculator = ledgerCalculator;
    }

    /**
     * Records money the provider received outside the system as a wallet top-up.
     *
     * @param provider provider performing the top-up
     * @param clientId client whose wallet is credited
     * @param amount   positive amount in the provider currency
     * @param comment  optional free-text note
     * @throws IllegalArgumentException if the actor is not the client's provider or the amount is invalid
     */
    @Transactional
    public void topUp(User provider, Long clientId, BigDecimal amount, String comment) {
        User managedProvider = requireProvider(provider);
        User client = requireLinkedClient(managedProvider, clientId);
        BigDecimal money = requirePositiveMoney(amount);

        Wallet wallet = getOrCreateWallet(managedProvider, client);
        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.TOP_UP, money, null, managedProvider, now(), comment));
    }

    /**
     * Sells a package of pre-paid sessions of a packageable offering to a client's
     * wallet. Payment is handled outside the system (as for a top-up), so no money
     * ledger entry is written — only the session grant.
     *
     * @param provider      provider selling the package
     * @param clientId      client who receives the package
     * @param offeringId    offering the sessions apply to (must belong to the provider and be packageable)
     * @param sessions      number of sessions (within the offering's min/max)
     * @param priceOverride total price, or {@code null} to use the offering's package price
     * @param comment       optional free-text note
     * @throws IllegalArgumentException if any actor/offering check fails, the offering
     *                                  is not packageable, the count is out of range, or
     *                                  the price is not positive
     */
    @Transactional
    public void sellPackage(User provider,
                            Long clientId,
                            Long offeringId,
                            int sessions,
                            BigDecimal priceOverride,
                            String comment) {
        User managedProvider = requireProvider(provider);
        User client = requireLinkedClient(managedProvider, clientId);
        Offering offering = requireProviderOffering(managedProvider, offeringId);
        requirePackageable(offering);
        requireSessionsInRange(offering, sessions);
        BigDecimal price = requirePositiveMoney(
                priceOverride != null ? priceOverride : offering.packagePriceFor(sessions));

        Wallet wallet = getOrCreateWallet(managedProvider, client);
        ServicePackage servicePackage = servicePackageRepository.save(new ServicePackage(
                wallet, offering, sessions, price, wallet.getCurrency(), managedProvider, now(), comment, PackageStatus.ACTIVE));
        walletEntryRepository.save(WalletEntry.session(
                wallet, WalletEntryType.PACKAGE_GRANT, sessions, servicePackage, null, managedProvider, now(), comment));
    }

    /**
     * Refunds a package to the client: credits the wallet with the given amount and
     * voids the package's still-unused sessions so they cannot be booked after the
     * refund. Sessions already booked (held) are left to run their course.
     *
     * <p>This is the only path that returns money against a package, and it is
     * provider-initiated — a client can never turn package sessions back into money.
     *
     * @param provider     provider issuing the refund
     * @param packageId    package being refunded (must belong to the provider)
     * @param refundAmount amount credited to the client's wallet (positive)
     * @param comment      optional free-text note
     * @throws IllegalArgumentException if the package is not found, does not belong
     *                                  to the provider, or the amount is not positive
     */
    @Transactional
    public void refundPackage(User provider, Long packageId, BigDecimal refundAmount, String comment) {
        User managedProvider = requireProvider(provider);
        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found"));
        Wallet wallet = servicePackage.getWallet();
        requireProviderOwnsWallet(managedProvider, wallet);
        if (servicePackage.getStatus() != PackageStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an active package can be refunded");
        }
        BigDecimal money = requirePositiveMoney(refundAmount);

        int unusedSessions = ledgerCalculator
                .foldSessions(walletEntryRepository.findAllByServicePackageOrderByIdAsc(servicePackage))
                .getAvailable();

        walletEntryRepository.save(WalletEntry.money(
                wallet, WalletEntryType.ADJUSTMENT, money, null, managedProvider, now(), comment));
        if (unusedSessions > 0) {
            walletEntryRepository.save(WalletEntry.session(
                    wallet, WalletEntryType.PACKAGE_REVOKE, unusedSessions, servicePackage, null, managedProvider, now(), comment));
        }
    }

    /**
     * A client requests a package of a packageable offering. The price is the
     * offering's package price for the chosen count (fixed by the provider). The
     * price is <em>reserved</em> on the client's wallet (a money HOLD) and the
     * package awaits provider approval — no sessions are granted yet. Mirrors an
     * appointment request.
     *
     * @param client     client requesting the package
     * @param offeringId packageable offering the sessions apply to
     * @param sessions   number of sessions (within the offering's min/max)
     * @param comment    optional free-text note
     * @throws IllegalArgumentException if the client has no provider or wallet, the
     *                                  offering is not packageable, the count is out
     *                                  of range, or the wallet lacks the funds
     */
    @Transactional
    public void requestPackage(User client, Long offeringId, int sessions, String comment) {
        User provider = providerClientLinkRepository.findByClient(client)
                .orElseThrow(() -> new IllegalArgumentException("Client is not linked to a provider"))
                .getProvider();
        Offering offering = requireProviderOffering(provider, offeringId);
        requirePackageable(offering);
        requireSessionsInRange(offering, sessions);
        BigDecimal price = offering.packagePriceFor(sessions);

        Wallet wallet = walletRepository.findByClient(client)
                .orElseThrow(() -> new IllegalArgumentException("Insufficient funds for this package"));
        BigDecimal available = ledgerCalculator
                .foldMoney(wallet.getCurrency(), walletEntryRepository.findAllByWalletOrderByIdAsc(wallet))
                .getAvailable();
        if (available.compareTo(price) < 0) {
            throw new IllegalArgumentException("Insufficient funds for this package");
        }

        ServicePackage servicePackage = servicePackageRepository.save(new ServicePackage(
                wallet, offering, sessions, price, wallet.getCurrency(), client, now(), comment, PackageStatus.REQUESTED));
        walletEntryRepository.save(WalletEntry.packageMoney(
                wallet, WalletEntryType.HOLD, price, servicePackage, client, now(), comment));
    }

    /**
     * Provider approves a requested package: the held price is settled (spent) and
     * the sessions are granted. Mirrors accepting an appointment request.
     *
     * @param provider  provider approving the request (must own the wallet)
     * @param packageId requested package identifier
     * @throws IllegalArgumentException if the package is not found or not the provider's
     * @throws IllegalStateException    if the package is not awaiting approval
     */
    @Transactional
    public void acceptPackageRequest(User provider, Long packageId) {
        User managedProvider = requireProvider(provider);
        ServicePackage servicePackage = requireOwnedPackage(managedProvider, packageId);

        servicePackage.activate();
        Wallet wallet = servicePackage.getWallet();
        walletEntryRepository.save(WalletEntry.packageMoney(
                wallet, WalletEntryType.SETTLE, servicePackage.getPrice(), servicePackage, managedProvider, now(), null));
        walletEntryRepository.save(WalletEntry.session(
                wallet, WalletEntryType.PACKAGE_GRANT, servicePackage.getTotalSessions(), servicePackage, null, managedProvider, now(), null));
    }

    /**
     * Provider declines a requested package: the held price is released back to the
     * client. Mirrors rejecting an appointment request.
     *
     * @param provider  provider declining the request (must own the wallet)
     * @param packageId requested package identifier
     * @throws IllegalArgumentException if the package is not found or not the provider's
     * @throws IllegalStateException    if the package is not awaiting approval
     */
    @Transactional
    public void rejectPackageRequest(User provider, Long packageId) {
        User managedProvider = requireProvider(provider);
        ServicePackage servicePackage = requireOwnedPackage(managedProvider, packageId);

        servicePackage.reject();
        Wallet wallet = servicePackage.getWallet();
        walletEntryRepository.save(WalletEntry.packageMoney(
                wallet, WalletEntryType.RELEASE, servicePackage.getPrice(), servicePackage, managedProvider, now(), null));
    }

    // ===== Private helpers =====

    private ServicePackage requireOwnedPackage(User provider, Long packageId) {
        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("Package not found"));
        requireProviderOwnsWallet(provider, servicePackage.getWallet());
        return servicePackage;
    }

    private void requireProviderOwnsWallet(User provider, Wallet wallet) {
        if (!wallet.getProvider().getId().equals(provider.getId())) {
            throw new IllegalArgumentException("Wallet does not belong to provider");
        }
    }

    private Wallet getOrCreateWallet(User provider, User client) {
        return walletRepository.findByClient(client)
                .orElseGet(() -> walletRepository.save(
                        new Wallet(client, provider, requireProviderCurrency(provider), now())));
    }

    private User requireProvider(User provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required");
        }

        User managed = userRepository.findById(provider.getId())
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));

        if (managed.getRole() != UserRole.PROVIDER) {
            throw new IllegalArgumentException("Only a provider can manage a wallet");
        }

        return managed;
    }

    private User requireLinkedClient(User provider, Long clientId) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client not found"));

        providerClientLinkRepository.findByProviderAndClientId(provider, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client is not linked to provider"));

        return client;
    }

    private Offering requireProviderOffering(User provider, Long offeringId) {
        return offeringRepository.findByProviderAndId(provider, offeringId)
                .orElseThrow(() -> new IllegalArgumentException("Offering not found for provider"));
    }

    private String requireProviderCurrency(User provider) {
        String currency = provider.getCurrency();
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("Provider currency is not set");
        }
        return currency;
    }

    private BigDecimal requirePositiveMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        if (amount.scale() > MAX_MONEY_SCALE) {
            throw new IllegalArgumentException("Amount has too many decimal places");
        }
        return amount;
    }

    private void requirePackageable(Offering offering) {
        if (!offering.isPackageable()) {
            throw new IllegalArgumentException("This service is not sold as a package");
        }
    }

    private void requireSessionsInRange(Offering offering, int sessions) {
        int min = offering.effectiveMinPackageSessions();
        int max = offering.effectiveMaxPackageSessions();
        if (sessions < min || sessions > max) {
            throw new IllegalArgumentException(
                    "Package sessions must be between " + min + " and " + max);
        }
    }

    private LocalDateTime now() {
        return applicationTimeService.currentUtcDateTime();
    }
}
