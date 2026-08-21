package life.wellnara.repository;

import life.wellnara.model.PackageStatus;
import life.wellnara.model.ServicePackage;
import life.wellnara.model.User;
import life.wellnara.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for service packages.
 */
public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

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
