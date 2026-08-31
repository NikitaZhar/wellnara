package life.wellnara.service;

import life.wellnara.exception.LocalizedException;
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
     * A client requests a package of {@code sessions} sessions of an offering and
     * picks the start of its first session. The price is the offering's total for
     * the chosen count ({@link Offering#totalPriceFor}): a single session at the
     * standard price, more at the package price when the offering has one. The
     * whole price is <em>reserved</em> on the client's wallet (a money HOLD) and
     * the package awaits provider approval — no sessions are granted yet. The
     * first session is scheduled from {@code firstSessionStartUtc} on approval.
     *
     * @param client               client requesting the package
     * @param offeringId           offering the sessions apply to
     * @param sessions             number of sessions (1 up to the offering's maximum)
     * @param firstSessionStartUtc chosen start of the first session, in UTC
     * @param comment              optional free-text note
     * @return the saved requested package
     * @throws IllegalArgumentException if the client has no provider or wallet, the
     *                                  count is out of range, or the wallet lacks the funds
     */
    @Transactional
    public ServicePackage requestPackage(User client,
                                         Long offeringId,
                                         int sessions,
                                         LocalDateTime firstSessionStartUtc,
                                         String comment) {
        User provider = providerClientLinkRepository.findByClient(client)
                .orElseThrow(() -> new LocalizedException("error.wallet.clientNoProvider", "Client is not linked to a provider"))
                .getProvider();
        Offering offering = requireProviderOffering(provider, offeringId);
        requireSessionsInRange(offering, sessions);
        BigDecimal price = offering.totalPriceFor(sessions);

        Wallet wallet = walletRepository.findByClient(client)
                .orElseThrow(() -> new LocalizedException("error.wallet.insufficientFunds", "Insufficient funds for this package"));
        BigDecimal available = ledgerCalculator
                .foldMoney(wallet.getCurrency(), walletEntryRepository.findAllByWalletOrderByIdAsc(wallet))
                .getAvailable();
        if (available.compareTo(price) < 0) {
            throw new LocalizedException("error.wallet.insufficientFunds", "Insufficient funds for this package");
        }

        ServicePackage servicePackage = servicePackageRepository.save(new ServicePackage(
                wallet, offering, sessions, price, wallet.getCurrency(), client, now(), comment,
                PackageStatus.REQUESTED, firstSessionStartUtc));
        walletEntryRepository.save(WalletEntry.packageMoney(
                wallet, WalletEntryType.HOLD, price, servicePackage, client, now(), comment));
        return servicePackage;
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

    /**
     * Client withdraws their own package request before the provider acts on it:
     * the held price is released back to the wallet. Mirrors
     * {@link #rejectPackageRequest} but is initiated by the owning client, so the
     * package ends in the same terminal state and the hold is released the same way.
     *
     * @param client    client withdrawing the request (must own the wallet)
     * @param packageId requested package identifier
     * @throws IllegalArgumentException if the package is not found or not the client's
     * @throws IllegalStateException    if the package is not awaiting approval
     */
    @Transactional
    public void cancelPackageRequestByClient(User client, Long packageId) {
        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .orElseThrow(() -> new LocalizedException("error.wallet.packageNotFound", "Package not found"));
        if (!servicePackage.getWallet().getClient().getId().equals(client.getId())) {
            throw new LocalizedException("error.wallet.packageNotClient", "Package does not belong to client");
        }

        servicePackage.reject();
        Wallet wallet = servicePackage.getWallet();
        walletEntryRepository.save(WalletEntry.packageMoney(
                wallet, WalletEntryType.RELEASE, servicePackage.getPrice(), servicePackage, client, now(), null));
    }

    // ===== Private helpers =====

    private ServicePackage requireOwnedPackage(User provider, Long packageId) {
        ServicePackage servicePackage = servicePackageRepository.findById(packageId)
                .orElseThrow(() -> new LocalizedException("error.wallet.packageNotFound", "Package not found"));
        requireProviderOwnsWallet(provider, servicePackage.getWallet());
        return servicePackage;
    }

    private void requireProviderOwnsWallet(User provider, Wallet wallet) {
        if (!wallet.getProvider().getId().equals(provider.getId())) {
            throw new LocalizedException("error.wallet.walletNotProvider", "Wallet does not belong to provider");
        }
    }

    private Wallet getOrCreateWallet(User provider, User client) {
        return walletRepository.findByClient(client)
                .orElseGet(() -> walletRepository.save(
                        new Wallet(client, provider, requireProviderCurrency(provider), now())));
    }

    private User requireProvider(User provider) {
        if (provider == null) {
            throw new LocalizedException("error.wallet.providerRequired", "Provider is required");
        }

        User managed = userRepository.findById(provider.getId())
                .orElseThrow(() -> new LocalizedException("error.wallet.providerNotFound", "Provider not found"));

        if (managed.getRole() != UserRole.PROVIDER) {
            throw new LocalizedException("error.wallet.onlyProvider", "Only a provider can manage a wallet");
        }

        return managed;
    }

    private User requireLinkedClient(User provider, Long clientId) {
        User client = userRepository.findById(clientId)
                .orElseThrow(() -> new LocalizedException("error.wallet.clientNotFound", "Client not found"));

        providerClientLinkRepository.findByProviderAndClientId(provider, clientId)
                .orElseThrow(() -> new LocalizedException("error.wallet.clientNotLinked", "Client is not linked to provider"));

        return client;
    }

    private Offering requireProviderOffering(User provider, Long offeringId) {
        return offeringRepository.findByProviderAndId(provider, offeringId)
                .orElseThrow(() -> new LocalizedException("error.wallet.offeringNotFound", "Offering not found for provider"));
    }

    private String requireProviderCurrency(User provider) {
        String currency = provider.getCurrency();
        if (currency == null || currency.isBlank()) {
            throw new LocalizedException("error.wallet.currencyNotSet", "Provider currency is not set");
        }
        return currency;
    }

    private BigDecimal requirePositiveMoney(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new LocalizedException("error.wallet.amountPositive", "Amount must be positive");
        }
        if (amount.scale() > MAX_MONEY_SCALE) {
            throw new LocalizedException("error.wallet.amountScale", "Amount has too many decimal places");
        }
        return amount;
    }

    private void requireSessionsInRange(Offering offering, int sessions) {
        int max = offering.effectiveMaxPackageSessions();
        if (sessions < 1 || sessions > max) {
            throw new LocalizedException("error.wallet.sessionsRange",
                    "Number of sessions must be between 1 and " + max, max);
        }
    }

    private LocalDateTime now() {
        return applicationTimeService.currentUtcDateTime();
    }
}
