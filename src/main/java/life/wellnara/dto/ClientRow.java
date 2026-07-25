package life.wellnara.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * View model for one row in the provider's "My clients" table.
 *
 * <p>Carries the human-facing display name and phone resolved from the client
 * profile, plus the client's spendable wallet balance and its currency, so the
 * clients table can show finances inline without the template traversing lazy
 * associations.
 */
public class ClientRow {

    private final Long clientId;
    private final String displayName;
    private final String email;
    private final String phone;
    private final LocalDateTime invitedAt;
    private final BigDecimal availableBalance;
    private final String currency;

    public ClientRow(Long clientId,
                     String displayName,
                     String email,
                     String phone,
                     LocalDateTime invitedAt,
                     BigDecimal availableBalance,
                     String currency) {
        this.clientId = clientId;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.invitedAt = invitedAt;
        this.availableBalance = availableBalance;
        this.currency = currency;
    }

    public Long getClientId() {
        return clientId;
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

    public LocalDateTime getInvitedAt() {
        return invitedAt;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public String getCurrency() {
        return currency;
    }
}
