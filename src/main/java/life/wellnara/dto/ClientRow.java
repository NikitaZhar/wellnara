package life.wellnara.dto;

import java.time.LocalDateTime;

/**
 * View model for one row in the provider's "My clients" table.
 *
 * <p>Carries the human-facing display name and phone resolved from the client profile,
 * so templates do not need to traverse lazy associations.
 */
public class ClientRow {

    private final Long clientId;
    private final String displayName;
    private final String email;
    private final String phone;
    private final LocalDateTime invitedAt;

    public ClientRow(Long clientId,
                     String displayName,
                     String email,
                     String phone,
                     LocalDateTime invitedAt) {
        this.clientId = clientId;
        this.displayName = displayName;
        this.email = email;
        this.phone = phone;
        this.invitedAt = invitedAt;
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
}
