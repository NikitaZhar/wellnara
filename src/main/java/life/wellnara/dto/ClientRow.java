package life.wellnara.dto;

import java.math.BigDecimal;

/**
 * View model for one row in the provider's "My clients" table.
 *
 * <p>Carries the human-facing display name and phone resolved from the client
 * profile, plus the client's spendable wallet balance and its currency, so the
 * clients table can show finances inline without the template traversing lazy
 * associations. The invitation date arrives already formatted for display
 * ({@code invitedAtLabel}) in the request locale, so the template renders it
 * verbatim without date logic.
 */
public class ClientRow {

    private final Long clientId;
    private final String displayName;
    private final String email;
    private final String phone;
    private final String invitedAtLabel;
    private final BigDecimal availableBalance;
    private final String currency;
    private final boolean active;

    public ClientRow(Long clientId,
                     String displayName,
                     String email,
                     String phone,
                     String invitedAtLabel,
                     BigDecimal availableBalance,
                     String currency,
                     boolean active) {
        this.clientId = clientId;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.invitedAtLabel = invitedAtLabel;
        this.availableBalance = availableBalance;
        this.currency = currency;
        this.active = active;
    }

    public Long getClientId() {
        return clientId;
    }

    public boolean isActive() {
        return active;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmail() {
        return email;
    }

    public String getPhone() {
        return phone;
    }

    public String getInvitedAtLabel() {
        return invitedAtLabel;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public String getCurrency() {
        return currency;
    }
}
