package life.wellnara.service;

import life.wellnara.model.User;
import life.wellnara.model.UserRole;
import life.wellnara.repository.OfferingRepository;
import life.wellnara.repository.UserRepository;
import life.wellnara.repository.WalletEntryRepository;
import life.wellnara.service.wallet.CurrencyCodes;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the provider's wallet currency.
 *
 * <p>A provider works in a single currency: their wallets and offering prices
 * inherit it. The currency may be set freely while the provider has no money
 * movements, but once any wallet ledger entry exists it is frozen — there is no
 * cross-rate conversion, so changing it would misvalue existing balances.
 */
@Service
public class ProviderCurrencyService {

    private final UserRepository userRepository;
    private final OfferingRepository offeringRepository;
    private final WalletEntryRepository walletEntryRepository;

    /**
     * Creates provider currency service.
     *
     * @param userRepository        repository for users
     * @param offeringRepository    repository for offerings
     * @param walletEntryRepository repository for wallet ledger entries
     */
    public ProviderCurrencyService(UserRepository userRepository,
                                   OfferingRepository offeringRepository,
                                   WalletEntryRepository walletEntryRepository) {
        this.userRepository = userRepository;
        this.offeringRepository = offeringRepository;
        this.walletEntryRepository = walletEntryRepository;
    }

    /**
     * Sets or changes the provider's currency and propagates it to the provider's
     * offerings.
     *
     * <p>A no-op when the code already matches. Rejected once any wallet of the
     * provider has ledger entries.
     *
     * @param provider     provider whose currency is changed
     * @param currencyCode target ISO 4217 currency code
     * @throws IllegalArgumentException if the user is not a provider, the code is
     *                                  invalid, or the wallet ledger is non-empty
     */
    @Transactional
    public void changeCurrency(User provider, String currencyCode) {
        User managed = requireProvider(provider);
        String normalized = CurrencyCodes.normalize(currencyCode);

        if (normalized.equals(managed.getCurrency())) {
            return;
        }

        if (walletEntryRepository.existsByWallet_Provider(managed)) {
            throw new IllegalArgumentException(
                    "Currency cannot be changed once the wallet ledger has entries");
        }

        managed.setCurrency(normalized);
        offeringRepository.findAllByProvider(managed)
                .forEach(offering -> offering.setCurrency(normalized));
    }

    private User requireProvider(User provider) {
        if (provider == null) {
            throw new IllegalArgumentException("Provider is required");
        }

        User managed = userRepository.findById(provider.getId())
                .orElseThrow(() -> new IllegalArgumentException("Provider not found"));

        if (managed.getRole() != UserRole.PROVIDER) {
            throw new IllegalArgumentException("Only a provider has a wallet currency");
        }

        return managed;
    }
}
