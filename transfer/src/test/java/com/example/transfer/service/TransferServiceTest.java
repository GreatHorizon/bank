package com.example.transfer.service;

import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.NotificationDto;
import com.example.shared.dto.TransferMoneyDto;
import com.example.transfer.client.AccountsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private AccountsClient accountsClient;

    @Mock
    private NotificationsClient notificationsClient;

    @InjectMocks
    private TransferService transferService;

    @Test
    void transferMoneyTransfersAndSendsNotification() {
        JwtAuthenticationToken authentication = jwtAuthenticationToken("john");
        TransferMoneyDto dto = new TransferMoneyDto("petr", 30L);

        when(accountsClient.getBalance("john")).thenReturn(100L);

        transferService.transferMoney(authentication, dto);

        verify(accountsClient).getBalance("john");
        verify(accountsClient).transfer("john", dto);

        ArgumentCaptor<NotificationDto> notificationCaptor =
                ArgumentCaptor.forClass(NotificationDto.class);

        verify(notificationsClient).sendNotification(notificationCaptor.capture());

        NotificationDto notification = notificationCaptor.getValue();

        assertThat(notification).isNotNull();
        assertThat(notification.service()).isEqualTo("transfer-service");
        assertThat(notification.amount()).isEqualTo(30L);
        assertThat(notification.toLogin()).isEqualTo("petr");
    }

    @Test
    void transferMoneyThrowsWhenAmountIsNull() {
        JwtAuthenticationToken authentication = jwtAuthenticationToken("john");
        TransferMoneyDto dto = new TransferMoneyDto("petr", null);

        assertThatThrownBy(() -> transferService.transferMoney(authentication, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Сумма должна быть больше нуля");

        verify(accountsClient, never()).getBalance("john");
        verify(accountsClient, never()).transfer("john", dto);
        verify(notificationsClient, never()).sendNotification(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transferMoneyThrowsWhenAmountIsZero() {
        JwtAuthenticationToken authentication = jwtAuthenticationToken("john");
        TransferMoneyDto dto = new TransferMoneyDto("petr", 0L);

        assertThatThrownBy(() -> transferService.transferMoney(authentication, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Сумма должна быть больше нуля");

        verify(accountsClient, never()).getBalance("john");
        verify(accountsClient, never()).transfer("john", dto);
        verify(notificationsClient, never()).sendNotification(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transferMoneyThrowsWhenAmountIsNegative() {
        JwtAuthenticationToken authentication = jwtAuthenticationToken("john");
        TransferMoneyDto dto = new TransferMoneyDto("petr", -10L);

        assertThatThrownBy(() -> transferService.transferMoney(authentication, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Сумма должна быть больше нуля");

        verify(accountsClient, never()).getBalance("john");
        verify(accountsClient, never()).transfer("john", dto);
        verify(notificationsClient, never()).sendNotification(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void transferMoneyThrowsWhenBalanceIsNotEnough() {
        JwtAuthenticationToken authentication = jwtAuthenticationToken("john");
        TransferMoneyDto dto = new TransferMoneyDto("petr", 150L);

        when(accountsClient.getBalance("john")).thenReturn(100L);

        assertThatThrownBy(() -> transferService.transferMoney(authentication, dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Недостаточно средств");

        verify(accountsClient).getBalance("john");
        verify(accountsClient, never()).transfer("john", dto);
        verify(notificationsClient, never()).sendNotification(org.mockito.ArgumentMatchers.any());
    }

    private JwtAuthenticationToken jwtAuthenticationToken(String login) {
        Map<String, Object> claims = login == null
                ? Map.of()
                : Map.of("preferred_username", login);

        Jwt jwt = new Jwt(
                "token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("alg", "none"),
                claims
        );

        return new JwtAuthenticationToken(jwt);
    }
}