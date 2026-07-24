package life.wellnara.repository;

import life.wellnara.model.Appointment;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for append-only wallet ledger entries.
 */
public interface WalletEntryRepository extends JpaRepository<WalletEntry, Long> {

    /**
     * Returns all entries of a wallet in insertion order, ready to be folded.
     *
     * @param wallet wallet whose ledger is read
     * @return ledger entries ordered by id
     */
    List<WalletEntry> findAllByWalletOrderByIdAsc(Wallet wallet);

    /**
     * Returns all entries belonging to a service package in insertion order.
     *
     * @param servicePackage package whose session entries are read
     * @return session entries ordered by id
     */
    List<WalletEntry> findAllByServicePackageOrderByIdAsc(ServicePackage servicePackage);

    /**
     * Returns all entries tied to an appointment in insertion order (hold and its
     * final release/settle), used to finalise or check idempotency.
     *
     * @param appointment appointment the entries relate to
     * @return entries ordered by id
     */
    List<WalletEntry> findAllByAppointmentOrderByIdAsc(Appointment appointment);

    /**
     * Whether any wallet of the given provider has at least one ledger entry.
     *
     * @param provider provider who owns the wallets
     * @return {@code true} if the provider's wallet ledger is non-empty
     */
    boolean existsByWallet_Provider(User provider);
}
