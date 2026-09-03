package life.wellnara.repository;

import life.wellnara.model.Offering;
import life.wellnara.model.PackageStatus;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for service packages.
 */
public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

    /**
     * Whether any package references the given offering, in any status.
     *
     * <p>Used to guard offering deletion: an offering referenced by a package
     * cannot be deleted, since {@code offering_id} is a non-null foreign key.
     *
     * @param offering offering to check
     * @return {@code true} if at least one package references it
     */
    boolean existsByOffering(Offering offering);

    /**
     * Whether the client has any package with the provider in one of the given
     * statuses. Used to block deactivating a client who still has open packages
     * (e.g. {@code REQUESTED} or {@code ACTIVE}). Resolved through the wallet, which
     * carries the client-provider pair.
     *
     * @param client   the client (wallet owner)
     * @param provider the provider (wallet holder)
     * @param statuses statuses to match
     * @return {@code true} if at least one matching package exists
     */
    boolean existsByWallet_ClientAndWallet_ProviderAndStatusIn(User client, User provider, Collection<PackageStatus> statuses);

    /**
     * Returns all packages of a wallet.
     *
     * @param wallet wallet owner
     * @return list of packages
     */
    List<ServicePackage> findAllByWallet(Wallet wallet);

    /**
     * Returns the provider's packages in a given status, oldest first — used to
     * list pending package requests.
     *
     * @param provider provider owning the wallets
     * @param status   lifecycle status to match
     * @return matching packages ordered by creation time
     */
    List<ServicePackage> findAllByWallet_ProviderAndStatusOrderByCreatedAtAsc(User provider, PackageStatus status);

    /**
     * Returns a client's packages in a given status, oldest first — used to show
     * the client their pending requests.
     *
     * @param client client owning the wallet
     * @param status lifecycle status to match
     * @return matching packages ordered by creation time
     */
    List<ServicePackage> findAllByWallet_ClientAndStatusOrderByCreatedAtAsc(User client, PackageStatus status);
}
