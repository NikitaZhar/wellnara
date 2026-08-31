package life.wellnara.service;

import life.wellnara.dto.ClientPackageView;
import life.wellnara.dto.ClientWalletView;
import life.wellnara.dto.PackageRemainder;
import life.wellnara.dto.WalletHistoryRow;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.SessionBalance;
import life.wellnara.model.User;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletBalance;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.dto.PackageRequestView;
import life.wellnara.model.PackageStatus;
import life.wellnara.repository.ProviderClientLinkRepository;
import life.wellnara.repository.ServicePackageRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.time.ApplicationTimeService;
import life.wellnara.service.wallet.WalletLedgerCalculator;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Read-only projections over the wallet ledger.
 *
 * <p>This is the query side of the wallet module (the write side lives in
 * {@link WalletCommandService} / {@link WalletReservationService} /
 * {@link AppointmentSettlementService}); the split follows the Command/Query
 * separation already used for appointments. Every balance here is a fold of the
 * append-only ledger through the single, tested {@link WalletLedgerCalculator} —
 * this service never re-implements the sign rules, so money and session
 * semantics have exactly one home.
 *
 * <p>History rows are localised: the ledger stores UTC, but timestamps are shown
 * in the provider's calendar timezone (the same zone appointments use), and entry
 * types are mapped to human-readable labels here rather than leaking the raw enum
 * into templates.
 */
@Service
public class WalletQueryService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final WalletRepository walletRepository;
    private final WalletEntryRepository walletEntryRepository;
    private final ProviderClientLinkRepository providerClientLinkRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final WalletLedgerCalculator ledgerCalculator;
    private final UserProfileService userProfileService;
    private final ApplicationTimeService applicationTimeService;
    private final MessageSource messageSource;

    /**
     * Creates the wallet query service.
     *
     * @param walletRepository             repository for wallets
     * @param walletEntryRepository        repository for wallet ledger entries
     * @param providerClientLinkRepository repository for provider-client links
     * @param servicePackageRepository     repository for service packages
     * @param ledgerCalculator             folds the ledger into money and session balances
     * @param userProfileService           resolves a client display name for the provider page
     * @param applicationTimeService       resolves the provider timezone for localised timestamps
     * @param messageSource                resolver of localized user-facing labels
     */
    public WalletQueryService(WalletRepository walletRepository,
                              WalletEntryRepository walletEntryRepository,
                              ProviderClientLinkRepository providerClientLinkRepository,
                              ServicePackageRepository servicePackageRepository,
                              WalletLedgerCalculator ledgerCalculator,
                              UserProfileService userProfileService,
                              ApplicationTimeService applicationTimeService,
                              MessageSource messageSource) {
        this.walletRepository = walletRepository;
        this.walletEntryRepository = walletEntryRepository;
        this.providerClientLinkRepository = providerClientLinkRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.ledgerCalculator = ledgerCalculator;
        this.userProfileService = userProfileService;
        this.applicationTimeService = applicationTimeService;
        this.messageSource = messageSource;
    }

    private String msg(String key, Object... args) {
        return messageSource.getMessage(key, args, LocaleContextHolder.getLocale());
    }

    /**
     * Pending package requests awaiting the provider's approval.
     *
     * @param provider authenticated provider
     * @return package requests with the client name, oldest first
     */
    @Transactional(readOnly = true)
    public List<PackageRequestView> getPendingPackageRequestsForProvider(User provider) {
        return servicePackageRepository
                .findAllByWallet_ProviderAndStatusOrderByCreatedAtAsc(provider, PackageStatus.REQUESTED).stream()
                .map(pkg -> toRequestView(pkg, userProfileService.resolveDisplayName(pkg.getWallet().getClient())))
                .toList();
    }

    /**
     * The client's own package requests still awaiting approval.
     *
     * @param client authenticated client
     * @return package requests, oldest first
     */
    @Transactional(readOnly = true)
    public List<PackageRequestView> getPendingPackageRequestsOfClient(User client) {
        return servicePackageRepository
                .findAllByWallet_ClientAndStatusOrderByCreatedAtAsc(client, PackageStatus.REQUESTED).stream()
                .map(pkg -> toRequestView(pkg, null))
                .toList();
    }

    private PackageRequestView toRequestView(ServicePackage pkg, String clientName) {
        return new PackageRequestView(pkg.getId(), pkg.getOffering().getId(), clientName,
                pkg.getOffering().getName(), pkg.getTotalSessions(), pkg.getPrice(), pkg.getCurrency(),
                firstSessionStartInProviderZone(pkg));
    }

    /**
     * Converts the package's requested first-session start from UTC to the
     * provider's timezone for display; {@code null} when no first session was
     * chosen (a provider-granted package).
     */
    private LocalDateTime firstSessionStartInProviderZone(ServicePackage pkg) {
        LocalDateTime startUtc = pkg.getFirstSessionStartUtc();
        if (startUtc == null) {
            return null;
        }
        ZoneId providerZone = applicationTimeService.resolveProviderCalendarZone(pkg.getWallet().getProvider());
        return startUtc.atZone(ZoneOffset.UTC).withZoneSameInstant(providerZone).toLocalDateTime();
    }

    /**
     * Builds the wallet view for a client to read their own finances.
     *
     * <p>A client cannot change their balance, so this is strictly read-only. When
     * the client has no wallet yet, the view shows zero balances in the provider
     * currency (resolved via the client-provider link) rather than failing, so the
     * Home tile always renders.
     *
     * @param client authenticated client
     * @return the client's wallet view; zeros in the provider currency if no wallet exists
     */
    @Transactional(readOnly = true)
    public ClientWalletView getWalletOfClient(User client) {
        return walletRepository.findByClient(client)
                .map(wallet -> buildView(wallet, null, null))
                .orElseGet(() -> emptyView(providerCurrencyOf(client), null, null));
    }

    /**
     * Lists the client's active packages, one row per offering (aggregated across
     * every package the client holds for that service), keeping only offerings that
     * still have sessions left (available or already booked). Reuses the same
     * session fold as the wallet view, so remaining counts cannot diverge.
     *
     * @param client authenticated client
     * @return active packages, or an empty list when the client has no wallet or none remain
     */
    @Transactional(readOnly = true)
    public List<ClientPackageView> getActivePackagesOfClient(User client) {
        return walletRepository.findByClient(client)
                .map(this::buildActivePackages)
                .orElseGet(List::of);
    }

    /**
     * Package context for a provider's appointment requests: for each appointment
     * covered by a package (it has a {@code PACKAGE_HOLD}), a short label naming the
     * package and its remaining sessions. Appointments paid with money are absent.
     *
     * @param appointmentIds appointment identifiers to describe
     * @return map of appointment id to a display label
     */
    @Transactional(readOnly = true)
    public Map<Long, String> packageLabelsForAppointments(Collection<Long> appointmentIds) {
        if (appointmentIds.isEmpty()) {
            return Map.of();
        }
        // Group the requested appointments by the package that covers them, so each
        // package's ledger is read once instead of once per appointment.
        Map<Long, ServicePackage> packagesById = new LinkedHashMap<>();
        Map<Long, List<Long>> requestedByPackageId = new LinkedHashMap<>();
        for (WalletEntry hold : walletEntryRepository
                .findAllByAppointment_IdInAndType(appointmentIds, WalletEntryType.PACKAGE_HOLD)) {
            ServicePackage servicePackage = hold.getServicePackage();
            packagesById.putIfAbsent(servicePackage.getId(), servicePackage);
            requestedByPackageId
                    .computeIfAbsent(servicePackage.getId(), id -> new ArrayList<>())
                    .add(hold.getAppointment().getId());
        }

        Map<Long, String> labels = new HashMap<>();
        for (Map.Entry<Long, List<Long>> entry : requestedByPackageId.entrySet()) {
            ServicePackage servicePackage = packagesById.get(entry.getKey());
            int total = servicePackage.getTotalSessions();
            if (total <= 1) {
                continue; // single-session package: no session number to show
            }
            Map<Long, Integer> sessionNumberByAppointment = sessionNumbersOf(servicePackage);
            for (Long appointmentId : entry.getValue()) {
                Integer number = sessionNumberByAppointment.get(appointmentId);
                if (number != null) {
                    labels.put(appointmentId, msg("wallet.sessionOf", number, total));
                }
            }
        }
        return labels;
    }

    /**
     * Numbers the still-live sessions of a package in booking order. A session is
     * live when its {@code PACKAGE_HOLD} has no matching {@code PACKAGE_RELEASE}:
     * the append-only ledger keeps the original hold after a cancellation, so a
     * cancelled-and-rebooked session must not inflate the count (nor exceed the
     * total). Cancelled sessions get no number.
     *
     * @param servicePackage the package whose sessions are numbered
     * @return map of appointment id to its 1-based number among the live sessions
     */
    private Map<Long, Integer> sessionNumbersOf(ServicePackage servicePackage) {
        List<WalletEntry> entries = walletEntryRepository.findAllByServicePackageOrderByIdAsc(servicePackage);

        Set<Long> releasedAppointmentIds = new HashSet<>();
        for (WalletEntry entry : entries) {
            if (entry.getType() == WalletEntryType.PACKAGE_RELEASE && entry.getAppointment() != null) {
                releasedAppointmentIds.add(entry.getAppointment().getId());
            }
        }

        Map<Long, Integer> sessionNumberByAppointment = new HashMap<>();
        int number = 0;
        for (WalletEntry entry : entries) {
            if (entry.getType() != WalletEntryType.PACKAGE_HOLD || entry.getAppointment() == null) {
                continue;
            }
            Long appointmentId = entry.getAppointment().getId();
            if (releasedAppointmentIds.contains(appointmentId)) {
                continue; // cancelled session: it no longer occupies a slot
            }
            sessionNumberByAppointment.put(appointmentId, ++number);
        }
        return sessionNumberByAppointment;
    }

    private List<ClientPackageView> buildActivePackages(Wallet wallet) {
        List<WalletEntry> entries = walletEntryRepository.findAllByWalletOrderByIdAsc(wallet);

        Map<Long, PackageAccumulator> byOfferingId = new LinkedHashMap<>();
        for (List<WalletEntry> packageEntries : groupSessionEntriesByPackage(entries).values()) {
            SessionBalance balance = ledgerCalculator.foldSessions(packageEntries);
            var servicePackage = packageEntries.get(0).getServicePackage();
            var offering = servicePackage.getOffering();
            byOfferingId
                    .computeIfAbsent(offering.getId(),
                            id -> new PackageAccumulator(offering.getId(), offering.getName(), offering.isActive()))
                    .add(servicePackage.getTotalSessions(), balance.getAvailable(), balance.getHeld());
        }

        List<ClientPackageView> views = new ArrayList<>();
        for (PackageAccumulator accumulator : byOfferingId.values()) {
            if (accumulator.hasSessionsLeft()) {
                views.add(accumulator.toView());
            }
        }
        return views;
    }

    /**
     * Builds the wallet view of one of the provider's clients.
     *
     * <p>The {@code /provider/**} route guard is the first access check; this
     * re-verifies the client belongs to the provider (defense in depth). When the
     * client has no wallet yet, an empty view in the provider currency is returned
     * so the provider can still open the page and top it up.
     *
     * @param provider authenticated provider
     * @param clientId client whose wallet is read
     * @return the client's wallet view
     * @throws IllegalArgumentException if the client is not linked to the provider
     */
    @Transactional(readOnly = true)
    public ClientWalletView getWalletForProvider(User provider, Long clientId) {
        User client = providerClientLinkRepository.findByProviderAndClientId(provider, clientId)
                .orElseThrow(() -> new IllegalArgumentException("Client is not linked to provider"))
                .getClient();
        String clientName = userProfileService.resolveDisplayName(client);

        return walletRepository.findByClient(client)
                .map(wallet -> buildView(wallet, client.getId(), clientName))
                .orElseGet(() -> emptyView(provider.getCurrency(), client.getId(), clientName));
    }

    /**
     * Computes the spendable (available) money balance for every client of the
     * provider, keyed by client id.
     *
     * <p>All of a provider's wallets share the provider currency, so the caller
     * pairs these amounts with {@code provider.getCurrency()} for display. Clients
     * without a wallet (no movements yet) are absent from the map; the caller
     * treats a missing entry as a zero balance. Uses one fetch-joined query for the
     * whole provider ledger and folds it per wallet through the shared calculator —
     * no query per client.
     *
     * @param provider authenticated provider
     * @return map of client id to available money for clients that have a wallet
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getClientBalances(User provider) {
        String currency = provider.getCurrency();

        Map<Long, List<WalletEntry>> entriesByClientId = new LinkedHashMap<>();
        for (WalletEntry entry : walletEntryRepository.findAllForProviderFetchingWalletAndClient(provider)) {
            Long clientId = entry.getWallet().getClient().getId();
            entriesByClientId.computeIfAbsent(clientId, id -> new ArrayList<>()).add(entry);
        }

        Map<Long, BigDecimal> availableByClientId = new LinkedHashMap<>();
        entriesByClientId.forEach((clientId, entries) ->
                availableByClientId.put(clientId, ledgerCalculator.foldMoney(currency, entries).getAvailable()));

        return availableByClientId;
    }

    // ===== Private helpers =====

    /**
     * Groups a wallet's session-ledger entries by their package id, preserving
     * insertion order. Money entries are skipped. Shared by the wallet-remainder
     * and active-package projections so the grouping lives in one place.
     */
    private Map<Long, List<WalletEntry>> groupSessionEntriesByPackage(List<WalletEntry> entries) {
        Map<Long, List<WalletEntry>> byPackageId = new LinkedHashMap<>();
        for (WalletEntry entry : entries) {
            if (entry.getType().isSession()) {
                byPackageId
                        .computeIfAbsent(entry.getServicePackage().getId(), id -> new ArrayList<>())
                        .add(entry);
            }
        }
        return byPackageId;
    }

    private ClientWalletView buildView(Wallet wallet, Long clientId, String clientName) {
        List<WalletEntry> entries = walletEntryRepository.findAllByWalletOrderByIdAsc(wallet);
        WalletBalance money = ledgerCalculator.foldMoney(wallet.getCurrency(), entries);
        ZoneId providerZone = applicationTimeService.resolveProviderCalendarZone(wallet.getProvider());

        return new ClientWalletView(
                clientId,
                clientName,
                true,
                wallet.getCurrency(),
                money.getAvailable(),
                money.getHeld(),
                buildPackageRemainders(entries),
                buildHistory(entries, providerZone));
    }

    private ClientWalletView emptyView(String currency, Long clientId, String clientName) {
        return new ClientWalletView(
                clientId, clientName, false, currency, BigDecimal.ZERO, BigDecimal.ZERO, List.of(), List.of());
    }

    /**
     * Folds the session ledger per package (grants keep insertion order) and keeps
     * only packages that still owe the client at least one session.
     */
    private List<PackageRemainder> buildPackageRemainders(List<WalletEntry> entries) {
        List<PackageRemainder> remainders = new ArrayList<>();
        for (List<WalletEntry> packageEntries : groupSessionEntriesByPackage(entries).values()) {
            SessionBalance sessions = ledgerCalculator.foldSessions(packageEntries);
            if (sessions.getTotal() <= 0) {
                continue;
            }
            ServicePackage servicePackage = packageEntries.get(0).getServicePackage();
            remainders.add(new PackageRemainder(servicePackage.getId(),
                    servicePackage.getOffering().getName(), sessions.getAvailable(), sessions.getHeld()));
        }
        return remainders;
    }

    /**
     * Maps the ledger to display rows, newest first (the ledger is stored oldest
     * first, so the list is reversed). Timestamps are converted from stored UTC to
     * the provider timezone.
     */
    private List<WalletHistoryRow> buildHistory(List<WalletEntry> entries, ZoneId providerZone) {
        List<WalletHistoryRow> rows = new ArrayList<>(entries.size());
        for (WalletEntry entry : entries) {
            String offeringName = entry.getServicePackage() != null
                    ? entry.getServicePackage().getOffering().getName()
                    : null;
            rows.add(new WalletHistoryRow(
                    formatInZone(entry.getCreatedAt(), providerZone),
                    entry.getType(),
                    labelOf(entry.getType()),
                    entry.getAmount(),
                    entry.getSessionCount(),
                    entry.getCurrency(),
                    offeringName,
                    entry.getComment()));
        }
        Collections.reverse(rows);
        return rows;
    }

    private String formatInZone(LocalDateTime utc, ZoneId providerZone) {
        return utc.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(providerZone)
                .format(TIMESTAMP_FORMAT);
    }

    /**
     * Human-readable label for a ledger entry type, shown to both client and
     * provider. Presentation text lives here, not on the domain enum.
     */
    private String labelOf(WalletEntryType type) {
        return switch (type) {
            case TOP_UP -> msg("wallet.entryType.topUp");
            case HOLD -> msg("wallet.entryType.hold");
            case RELEASE -> msg("wallet.entryType.release");
            case SETTLE -> msg("wallet.entryType.settle");
            case ADJUSTMENT -> msg("wallet.entryType.adjustment");
            case PACKAGE_GRANT -> msg("wallet.entryType.packageGrant");
            case PACKAGE_HOLD -> msg("wallet.entryType.packageHold");
            case PACKAGE_RELEASE -> msg("wallet.entryType.packageRelease");
            case PACKAGE_CONSUME -> msg("wallet.entryType.packageConsume");
            case PACKAGE_REVOKE -> msg("wallet.entryType.packageRevoke");
        };
    }

    private String providerCurrencyOf(User client) {
        return providerClientLinkRepository.findByClient(client)
                .map(link -> link.getProvider().getCurrency())
                .orElse(null);
    }

    /**
     * Accumulates several packages of the same offering into one client-facing row.
     */
    private static final class PackageAccumulator {

        private final Long offeringId;
        private final String offeringName;
        private final boolean offeringActive;
        private int total;
        private int remaining;
        private int pending;

        private PackageAccumulator(Long offeringId, String offeringName, boolean offeringActive) {
            this.offeringId = offeringId;
            this.offeringName = offeringName;
            this.offeringActive = offeringActive;
        }

        private void add(int grantedTotal, int available, int held) {
            this.total += grantedTotal;
            this.remaining += available;
            this.pending += held;
        }

        private boolean hasSessionsLeft() {
            return remaining + pending > 0;
        }

        private ClientPackageView toView() {
            return new ClientPackageView(offeringId, offeringName, offeringActive, total, remaining, pending);
        }
    }
}
