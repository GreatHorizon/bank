package com.example.cash.service;

import com.example.cash.client.AccountsClient;
import com.example.cash.metrics.CashMetrics;
import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.NotificationDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class CashService {

    final AccountsClient accountsClient;
    final NotificationsClient notificationsClient;
    final CashMetrics cashMetrics;

    private static final Logger log = LoggerFactory.getLogger(CashService.class);


    public CashService(AccountsClient accountsClient, NotificationsClient notificationsClient, CashMetrics cashMetrics) {
        this.accountsClient = accountsClient;
        this.notificationsClient = notificationsClient;
        this.cashMetrics = cashMetrics;
    }


    public void putCash(Authentication authentication, Long amount) {
        final var login = getLogin(authentication);

        accountsClient.putCash(login, amount);

        log.info("Sending cash putCash notification: login={}", login);

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
            log.error(e.getMessage());

            throw e;
        }
    }

    private void performGetCash(String login, Long amount) {
        log.info("Sending getCash request to accounts");

        accountsClient.getCash(login, amount);

        log.info("Sending cash getCash notification: login={}", login);

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
            log.error("Authentication object is not of type JwtAuthenticationToken");
            throw new IllegalStateException(
                    "Expected JwtAuthenticationToken, got: " + authentication.getClass()
            );
        }

        String login = jwtAuthenticationToken.getToken().getClaimAsString("preferred_username");

        if (login == null || login.isBlank()) {
            log.error("Authentication object is not of type preferred_username");

            throw new IllegalStateException("JWT does not contain preferred_username");
        }

        return login;
    }
}
