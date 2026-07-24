package life.wellnara.service;

import life.wellnara.model.Offering;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.model.Wallet;
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
                                ApplicationTimeService applicationTimeService) {
        this.walletRepository = walletRepository;
        this.walletEntryRepository = walletEntryRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.userRepository = userRepository;
        this.offeringRepository = offeringRepository;
        this.providerClientLinkRepository = providerClientLinkRepository;
        this.applicationTimeService = applicationTimeService;
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
     * Grants a package of pre-paid sessions to a client's wallet.
     *
     * @param provider      provider granting the package
     * @param clientId      client who receives the package
     * @param offeringId    offering the sessions apply to (must belong to the provider)
     * @param totalSessions number of sessions granted (positive)
     * @param price         total price paid, in the provider currency (positive)
     * @param comment       optional free-text note
     * @throws IllegalArgumentException if any actor/offering check or amount/count check fails
     */
    @Transactional
    public void grantPackage(User provider,
                             Long clientId,
                             Long offeringId,
                             int totalSessions,
                             BigDecimal price,
                             String comment) {
        User managedProvider = requireProvider(provider);
        User client = requireLinkedClient(managedProvider, clientId);
        Offering offering = requireProviderOffering(managedProvider, offeringId);
        requirePositiveSessions(totalSessions);
        BigDecimal money = requirePositiveMoney(price);

        Wallet wallet = getOrCreateWallet(managedProvider, client);
        ServicePackage servicePackage = servicePackageRepository.save(new ServicePackage(
                wallet, offering, totalSessions, money, wallet.getCurrency(), managedProvider, now(), comment));
        walletEntryRepository.save(WalletEntry.session(
                wallet, WalletEntryType.PACKAGE_GRANT, totalSessions, servicePackage, null, managedProvider, now(), comment));
    }

    // ===== Private helpers =====

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

    private void requirePositiveSessions(int totalSessions) {
        if (totalSessions <= 0) {
            throw new IllegalArgumentException("Package must grant at least one session");
        }
    }

    private LocalDateTime now() {
        return applicationTimeService.currentUtcDateTime();
    }
}
