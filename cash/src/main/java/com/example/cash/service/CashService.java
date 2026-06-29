package com.example.cash.service;

import com.example.cash.client.AccountsClient;
import com.example.cash.metrics.CashMetrics;
import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.NotificationDto;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CashService {

    final AccountsClient accountsClient;
    final NotificationsClient notificationsClient;
    final CashMetrics cashMetrics;

    public CashService(AccountsClient accountsClient, NotificationsClient notificationsClient, CashMetrics cashMetrics) {
        this.accountsClient = accountsClient;
        this.notificationsClient = notificationsClient;
        this.cashMetrics = cashMetrics;
    }


    public void putCash(Authentication authentication, Long amount) {
        final var login = getLogin(authentication);

        accountsClient.putCash(login, amount);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "putCash",
                        amount,
                        login,
                        null
                )
        );
    }

    public void getCash(Authentication authentication, Long amount) {
        final var login = getLogin(authentication);

        try {
            performGetCash(login, amount);
        } catch (Exception e) {
            cashMetrics.failedWithdrawal(login);

            throw e;
        }
    }

    private void performGetCash(String login, Long amount) {
        final var balance = accountsClient.getBalance(login);

        if (amount > balance) {
            throw new IllegalArgumentException("Amount greater than balance");
        }

        accountsClient.getCash(login, amount);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "getCash",
                        amount,
                        login,
                        null
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
