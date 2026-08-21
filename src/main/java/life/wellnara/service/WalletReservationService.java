package life.wellnara.service;

import life.wellnara.model.Appointment;
import life.wellnara.model.Offering;
import life.wellnara.model.PackageStatus;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.ServicePackageRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.repository.WalletRepository;
import life.wellnara.service.time.ApplicationTimeService;
import life.wellnara.service.wallet.WalletLedgerCalculator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Reserves a client's funds against an appointment.
 *
 * <p>Reservation is a two-step, single-transaction flow: {@link #planReservation}
 * decides how one session is covered — a package session if a package covers the
 * offering and still has one available, otherwise money against the available
 * balance — and fails fast if nothing covers it. The caller then creates the
 * appointment and calls {@link #applyReservation}, which appends the single
 * append-only hold entry ({@link WalletEntryType#PACKAGE_HOLD} or
 * {@link WalletEntryType#HOLD}) tied to it.
 *
 * <p>Planning before the appointment is created means an unaffordable request
 * never persists an appointment, and the hold and the appointment always commit
 * or roll back together. Sufficiency is checked against <em>available</em> (a
 * fold of the ledger), never the full balance.
 */
@Service
public class WalletReservationService {

    private final WalletRepository walletRepository;
    private final WalletEntryRepository walletEntryRepository;
    private final ServicePackageRepository servicePackageRepository;
    private final WalletLedgerCalculator ledgerCalculator;
    private final ApplicationTimeService applicationTimeService;

    /**
     * Creates wallet reservation service.
     *
     * @param walletRepository         repository for wallets
     * @param walletEntryRepository    repository for wallet ledger entries
     * @param servicePackageRepository repository for service packages
     * @param ledgerCalculator         folds the ledger into money and session balances
     * @param applicationTimeService   source of current application time (UTC)
     */
    public WalletReservationService(WalletRepository walletRepository,
                                    WalletEntryRepository walletEntryRepository,
                                    ServicePackageRepository servicePackageRepository,
                                    WalletLedgerCalculator ledgerCalculator,
                                    ApplicationTimeService applicationTimeService) {
        this.walletRepository = walletRepository;
        this.walletEntryRepository = walletEntryRepository;
        this.servicePackageRepository = servicePackageRepository;
        this.ledgerCalculator = ledgerCalculator;
        this.applicationTimeService = applicationTimeService;
    }

    /**
     * A decided but not-yet-written reservation: exactly one of a covering package
     * or a money amount is set.
     *
     * @param wallet         the client's wallet
     * @param servicePackage covering package, or {@code null} for a money hold
     * @param amount         money to hold, or {@code null} for a package hold
     */
    public record Reservation(Wallet wallet, ServicePackage servicePackage, BigDecimal amount) {

        private boolean isPackage() {
            return servicePackage != null;
        }
    }

    /**
     * Decides how one session for the offering is covered, without writing anything.
     *
     * @param client   client requesting the appointment
     * @param offering offering being booked
     * @return the planned reservation
     * @throws IllegalArgumentException if the client has no wallet or neither a package
     *                                  session nor enough money is available
     */
    @Transactional(readOnly = true)
    public Reservation planReservation(User client, Offering offering) {
        Wallet wallet = walletRepository.findByClient(client).orElseThrow(this::insufficientFunds);

        Optional<ServicePackage> coveringPackage = findCoveringPackage(wallet, offering);
        if (coveringPackage.isPresent()) {
            return new Reservation(wallet, coveringPackage.get(), null);
        }

        BigDecimal price = offering.getPricePerSession();
        BigDecimal available = ledgerCalculator
                .foldMoney(wallet.getCurrency(), walletEntryRepository.findAllByWalletOrderByIdAsc(wallet))
                .getAvailable();
        if (available.compareTo(price) < 0) {
            throw insufficientFunds();
        }

        return new Reservation(wallet, null, price);
    }

    /**
     * Writes the planned hold, tying it to the created appointment.
     *
     * @param reservation the plan from {@link #planReservation}
     * @param appointment the persisted appointment the hold belongs to
     */
    @Transactional
    public void applyReservation(Reservation reservation, Appointment appointment) {
        User client = appointment.getClient();
        LocalDateTime now = applicationTimeService.currentUtcDateTime();

        if (reservation.isPackage()) {
            walletEntryRepository.save(WalletEntry.session(reservation.wallet(),
                    WalletEntryType.PACKAGE_HOLD, 1, reservation.servicePackage(), appointment, client, now, null));
        } else {
            walletEntryRepository.save(WalletEntry.money(reservation.wallet(),
                    WalletEntryType.HOLD, reservation.amount(), appointment, client, now, null));
        }
    }

    private Optional<ServicePackage> findCoveringPackage(Wallet wallet, Offering offering) {
        return servicePackageRepository.findAllByWallet(wallet).stream()
                .filter(servicePackage -> servicePackage.getStatus() == PackageStatus.ACTIVE)
                .filter(servicePackage -> servicePackage.getOffering().getId().equals(offering.getId()))
                .filter(this::hasAvailableSession)
                .findFirst();
    }

    private boolean hasAvailableSession(ServicePackage servicePackage) {
        return ledgerCalculator
                .foldSessions(walletEntryRepository.findAllByServicePackageOrderByIdAsc(servicePackage))
                .getAvailable() >= 1;
    }

    private IllegalArgumentException insufficientFunds() {
        return new IllegalArgumentException("Insufficient funds for this appointment");
    }
}
