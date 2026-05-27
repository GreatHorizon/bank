package com.example.transfer.service;

import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.NotificationDto;
import com.example.shared.dto.TransferMoneyDto;
import com.example.transfer.client.AccountsClient;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final AccountsClient accountsClient;
    private final NotificationsClient notificationsClient;

    public TransferService(AccountsClient accountsClient, NotificationsClient notificationsClient) {
        this.accountsClient = accountsClient;
        this.notificationsClient = notificationsClient;
    }

    public void transferMoney(Authentication authentication, TransferMoneyDto transferMoneyDto) {
        if (transferMoneyDto.amount() == null || transferMoneyDto.amount() <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        final var login = getLogin(authentication);

        final var fromBalance = accountsClient.getBalance(login);

        if (fromBalance < transferMoneyDto.amount()) {
            throw new IllegalArgumentException("Недостаточно средств");
        }

        accountsClient.transfer(login, transferMoneyDto);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "putCash",
                        "transfer-service",
                        transferMoneyDto.amount(), login,
                        transferMoneyDto.login()
                )
        );
    }

    private String getLogin(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            throw new IllegalStateException(
                    "Expected JwtAuthenticationToken, got: " + authentication.getClass()
            );
        }

        String login = jwtAuthenticationToken.getToken().getClaimAsString("preferred_username");

        if (login == null || login.isBlank()) {
            throw new IllegalStateException("JWT does not contain preferred_username");
        }

        return login;
    }
}
