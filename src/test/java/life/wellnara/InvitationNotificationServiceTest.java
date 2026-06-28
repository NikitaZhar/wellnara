package life.wellnara;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import life.wellnara.service.email.EmailService;
import life.wellnara.service.email.InvitationNotificationService;

import static org.mockito.ArgumentMatchers.any;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link InvitationNotificationService}.
 *
 * <p>Verifies the product behavior that matters: an invitation email is dispatched to the
 * correct recipient and its body carries a registration link containing the token.
 * {@link EmailService} is a collaborator whose call is captured and asserted, so email
 * content is checked without any SMTP dependency.
 */
class InvitationNotificationServiceTest {

    private static final String BASE_URL = "https://app.wellnara.life";
    private static final String TOKEN = "11111111-2222-3333-4444-555555555555";

    @Test
    @DisplayName("Should send provider invitation to the recipient with a registration link carrying the token")
    void shouldSendProviderInvitationWithRegistrationLink() {
        EmailService emailService = mock(EmailService.class);
        InvitationNotificationService notificationService =
                new InvitationNotificationService(emailService, BASE_URL);

        notificationService.sendProviderInvitation("provider@example.com", TOKEN);

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPlainTextEmail(recipient.capture(), any(), body.capture());

        assertThat(recipient.getValue()).isEqualTo("provider@example.com");
        assertThat(body.getValue()).contains(BASE_URL + "/provider/register?token=" + TOKEN);
    }

    @Test
    @DisplayName("Should send client invitation to the recipient with a registration link carrying the token")
    void shouldSendClientInvitationWithRegistrationLink() {
        EmailService emailService = mock(EmailService.class);
        InvitationNotificationService notificationService =
                new InvitationNotificationService(emailService, BASE_URL);

        notificationService.sendClientInvitation("client@example.com", TOKEN);

        ArgumentCaptor<String> recipient = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendPlainTextEmail(recipient.capture(), any(), body.capture());

        assertThat(recipient.getValue()).isEqualTo("client@example.com");
        assertThat(body.getValue()).contains(BASE_URL + "/client/register?token=" + TOKEN);
    }
}
