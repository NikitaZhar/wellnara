package life.wellnara.repository;

import life.wellnara.model.Appointment;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.Wallet;
import life.wellnara.model.WalletEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
     * Loads every ledger entry of every wallet held with the given provider, in a
     * single query, ordered by wallet then insertion order and ready to be folded
     * per wallet.
     *
     * <p>Used to compute all of a provider's clients' balances at once (clients
     * table column and Home summary) without a query per wallet. The wallet and
     * its client are fetch-joined so the caller can group by client without
     * triggering lazy loads.
     *
     * <p>Scale note (see working agreement, section 2): this is one query but it
     * materialises the provider's whole ledger in memory. It is correct and
     * N+1-free for the current phase; at large ledger sizes it should move to a
     * database-side aggregate. Flagged, not yet optimised.
     *
     * @param provider provider whose wallets are read
     * @return all entries of the provider's wallets, ordered by wallet id then entry id
     */
    @Query("""
            select e from WalletEntry e
            join fetch e.wallet w
            join fetch w.client
            where w.provider = :provider
            order by w.id asc, e.id asc
            """)
    List<WalletEntry> findAllForProviderFetchingWalletAndClient(@Param("provider") User provider);

    /**
     * Whether any wallet of the given provider has at least one ledger entry.
     *
     * @param provider provider who owns the wallets
     * @return {@code true} if the provider's wallet ledger is non-empty
     */
    boolean existsByWallet_Provider(User provider);
}
