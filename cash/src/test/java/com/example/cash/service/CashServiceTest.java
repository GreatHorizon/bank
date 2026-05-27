package com.example.cash.service;

import com.example.cash.client.AccountsClient;
import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.NotificationDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CashServiceTest {

    @Mock
    private AccountsClient accountsClient;

    @Mock
    private NotificationsClient notificationsClient;

    @InjectMocks
    private CashService cashService;

    @Test
    void putCashAddsCashAndSendsNotification() {
        JwtAuthenticationToken authentication = authentication("john");

        cashService.putCash(authentication, 50L);

        verify(accountsClient).putCash("john", 50L);

        ArgumentCaptor<NotificationDto> notificationCaptor =
                ArgumentCaptor.forClass(NotificationDto.class);

        verify(notificationsClient).sendNotification(notificationCaptor.capture());

        NotificationDto notification = notificationCaptor.getValue();

        assertThat(notification.service()).isEqualTo("cash-service");
        assertThat(notification.amount()).isEqualTo(50L);
        assertThat(notification.login()).isEqualTo("john");
    }

    @Test
    void getCashWithdrawsCashAndSendsNotificationWhenBalanceIsEnough() {
        JwtAuthenticationToken authentication = authentication("john");

        when(accountsClient.getBalance("john")).thenReturn(100L);

        cashService.getCash(authentication, 40L);

        verify(accountsClient).getBalance("john");
        verify(accountsClient).getCash("john", 40L);

        ArgumentCaptor<NotificationDto> notificationCaptor =
                ArgumentCaptor.forClass(NotificationDto.class);

        verify(notificationsClient).sendNotification(notificationCaptor.capture());

        NotificationDto notification = notificationCaptor.getValue();

        assertThat(notification.service()).isEqualTo("cash-service");
        assertThat(notification.amount()).isEqualTo(40L);
        assertThat(notification.login()).isEqualTo("john");
    }

    @Test
    void getCashThrowsExceptionWhenAmountGreaterThanBalance() {
        JwtAuthenticationToken authentication = authentication("john");

        when(accountsClient.getBalance("john")).thenReturn(30L);

        assertThatThrownBy(() -> cashService.getCash(authentication, 40L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Amount greater than balance");

        verify(accountsClient).getBalance("john");
        verify(accountsClient, never()).getCash("john", 40L);
        verify(notificationsClient, never()).sendNotification(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void putCashThrowsExceptionWhenJwtDoesNotContainPreferredUsername() {
        JwtAuthenticationToken authentication = authenticationWithoutLogin();

        assertThatThrownBy(() -> cashService.putCash(authentication, 50L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT does not contain preferred_username");

        verify(accountsClient, never()).putCash(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
        verify(notificationsClient, never()).sendNotification(org.mockito.ArgumentMatchers.any());
    }

    private JwtAuthenticationToken authentication(String login) {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", login)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        return new JwtAuthenticationToken(jwt);
    }

    private JwtAuthenticationToken authenticationWithoutLogin() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        return new JwtAuthenticationToken(jwt);
    }
}