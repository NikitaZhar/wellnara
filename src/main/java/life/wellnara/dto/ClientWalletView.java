package life.wellnara.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Read model of a single client's wallet: money balance, remaining package
 * sessions and the full movement history.
 *
 * <p>Assembled by the query service by folding the append-only ledger; the view
 * holds no logic beyond exposing the folded figures. The same view backs both
 * the client's own read-only Home tile and the provider's per-client wallet
 * page, so it carries the {@code currency} even when the wallet does not exist
 * yet (a client with no movements): in that case balances are zero and the lists
 * are empty, but the provider currency is still shown. {@code clientId} /
 * {@code clientName} identify the subject on the provider page and are
 * {@code null} on the client's own tile.
 */
public class ClientWalletView {

    private final Long clientId;
    private final String clientName;
    private final String clientEmail;
    private final String clientPhone;
    private final boolean walletExists;
    private final String currency;
    private final BigDecimal available;
    private final BigDecimal held;
    private final List<PackageRemainder> packages;
    private final List<WalletHistoryRow> history;

    /**
     * Creates a client wallet view.
     *
     * @param clientId     subject client id (provider page), or {@code null} on the client's own tile
     * @param clientName   subject client display name (provider page), or {@code null}
     * @param clientEmail  subject client email (provider page), or {@code null} on the client's own tile
     * @param clientPhone  subject client phone (provider page), or {@code null} when unknown or on the client's own tile
     * @param walletExists whether a wallet has been created for the client yet
     * @param currency     ISO 4217 currency code (provider currency when no wallet exists)
     * @param available    spendable money balance
     * @param held         money reserved against pending appointments
     * @param packages     remaining sessions per non-empty package
     * @param history      ledger movements, newest first
     */
    public ClientWalletView(Long clientId,
                            String clientName,
                            String clientEmail,
                            String clientPhone,
                            boolean walletExists,
                            String currency,
                            BigDecimal available,
                            BigDecimal held,
                            List<PackageRemainder> packages,
                            List<WalletHistoryRow> history) {
        this.clientId = clientId;
        this.clientName = clientName;
        this.clientEmail = clientEmail;
        this.clientPhone = clientPhone;
        this.walletExists = walletExists;
        this.currency = currency;
        this.available = available;
        this.held = held;
        this.packages = packages;
        this.history = history;
    }

    public Long getClientId() {
        return clientId;
    }

    public String getClientName() {
        return clientName;
    }

    public String getClientEmail() {
        return clientEmail;
    }

    public String getClientPhone() {
        return clientPhone;
    }

    public boolean isWalletExists() {
        return walletExists;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getAvailable() {
        return available;
    }

    public BigDecimal getHeld() {
        return held;
    }

    public List<PackageRemainder> getPackages() {
        return packages;
    }

    public List<WalletHistoryRow> getHistory() {
        return history;
    }

    /**
     * Whether the client has any remaining package sessions to show.
     *
     * @return {@code true} if at least one non-empty package exists
     */
    public boolean isHasPackages() {
        return !packages.isEmpty();
    }

    /**
     * Whether the ledger has any movements to show.
     *
     * @return {@code true} if the history is non-empty
     */
    public boolean isHasHistory() {
        return !history.isEmpty();
    }
}
