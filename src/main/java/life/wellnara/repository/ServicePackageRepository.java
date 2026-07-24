package life.wellnara.repository;

import life.wellnara.model.ServicePackage;
import life.wellnara.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for service-package grants.
 */
public interface ServicePackageRepository extends JpaRepository<ServicePackage, Long> {

    /**
     * Returns all packages granted to a wallet.
     *
     * @param wallet wallet owner
     * @return list of packages
     */
    List<ServicePackage> findAllByWallet(Wallet wallet);
}
