package life.wellnara.service;

import life.wellnara.model.Appointment;
import life.wellnara.model.User;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import life.wellnara.model.WalletEntryType;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.service.time.ApplicationTimeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Finalises the hold placed for an appointment: either releasing it back to the
 * client or settling it as a delivered service.
 *
 * <p>The final entry mirrors the hold — money ({@code HOLD → RELEASE|SETTLE}) or a
 * package session ({@code PACKAGE_HOLD → PACKAGE_RELEASE|PACKAGE_CONSUME}) — and is
 * tied to the same appointment. Both operations are <strong>idempotent</strong>:
 * exactly one final entry exists per appointment, so replaying release or settle
 * does nothing. They are also safe on appointments with no hold (nothing happens),
 * which keeps pre-wallet appointments working.
 */
@Service
public class AppointmentSettlementService {

    private final WalletEntryRepository walletEntryRepository;
    private final ApplicationTimeService applicationTimeService;

    /**
     * Creates appointment settlement service.
     *
     * @param walletEntryRepository  repository for wallet ledger entries
     * @param applicationTimeService source of current application time (UTC)
     */
    public AppointmentSettlementService(WalletEntryRepository walletEntryRepository,
                                        ApplicationTimeService applicationTimeService) {
        this.walletEntryRepository = walletEntryRepository;
        this.applicationTimeService = applicationTimeService;
    }

    /**
     * Releases the appointment hold back to the client (money to available, or the
     * package session returned). No-op if there is no hold or it was already finalised.
     *
     * @param appointment appointment whose hold is released
     * @param actor       user recorded as the author of the entry
     */
    @Transactional
    public void release(Appointment appointment, User actor) {
        finalizeHold(appointment, actor, false);
    }

    /**
     * Settles the appointment hold as a delivered service (money consumed, or the
     * package session consumed). No-op if there is no hold or it was already finalised.
     *
     * @param appointment appointment whose hold is settled
     * @param actor       user recorded as the author of the entry
     */
    @Transactional
    public void settle(Appointment appointment, User actor) {
        finalizeHold(appointment, actor, true);
    }

    private void finalizeHold(Appointment appointment, User actor, boolean settle) {
        List<WalletEntry> entries = walletEntryRepository.findAllByAppointmentOrderByIdAsc(appointment);
        if (entries.stream().anyMatch(entry -> isFinal(entry.getType()))) {
            return;
        }

        Optional<WalletEntry> hold = entries.stream()
                .filter(entry -> isHold(entry.getType()))
                .findFirst();
        if (hold.isEmpty()) {
            return;
        }

        WalletEntry heldEntry = hold.get();
        Wallet wallet = heldEntry.getWallet();
        LocalDateTime now = applicationTimeService.currentUtcDateTime();

        if (heldEntry.getType() == WalletEntryType.PACKAGE_HOLD) {
            WalletEntryType type = settle ? WalletEntryType.PACKAGE_CONSUME : WalletEntryType.PACKAGE_RELEASE;
            walletEntryRepository.save(WalletEntry.session(
                    wallet, type, heldEntry.getSessionCount(), heldEntry.getServicePackage(), appointment, actor, now, null));
        } else {
            WalletEntryType type = settle ? WalletEntryType.SETTLE : WalletEntryType.RELEASE;
            walletEntryRepository.save(WalletEntry.money(
                    wallet, type, heldEntry.getAmount(), appointment, actor, now, null));
        }
    }

    private boolean isHold(WalletEntryType type) {
        return type == WalletEntryType.HOLD || type == WalletEntryType.PACKAGE_HOLD;
    }

    private boolean isFinal(WalletEntryType type) {
        return type == WalletEntryType.RELEASE
                || type == WalletEntryType.SETTLE
                || type == WalletEntryType.PACKAGE_RELEASE
                || type == WalletEntryType.PACKAGE_CONSUME;
    }
}
