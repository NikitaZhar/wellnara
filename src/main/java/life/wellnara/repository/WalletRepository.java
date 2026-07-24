package life.wellnara.repository;

import life.wellnara.model.User;
import life.wellnara.model.Wallet;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Repository for client wallets.
 */
public interface WalletRepository extends JpaRepository<Wallet, Long> {

    /**
     * Finds the wallet owned by the given client.
     *
     * @param client client owner
     * @return the client's wallet, or empty if none exists yet
     */
    Optional<Wallet> findByClient(User client);

    /**
     * Whether a wallet already exists for the given client.
     *
     * @param client client owner
     * @return {@code true} if the client has a wallet
     */
    boolean existsByClient(User client);
}
