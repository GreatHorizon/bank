package com.example.transfer.service;

import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.NotificationDto;
import com.example.shared.dto.TransferMoneyDto;
import com.example.transfer.client.AccountsClient;
import com.example.transfer.metrics.TransferMetrics;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final AccountsClient accountsClient;
    private final NotificationsClient notificationsClient;
    private final TransferMetrics transferMetrics;

    public TransferService(AccountsClient accountsClient, NotificationsClient notificationsClient, TransferMetrics transferMetrics) {
        this.accountsClient = accountsClient;
        this.notificationsClient = notificationsClient;
        this.transferMetrics = transferMetrics;
    }


    private void performTransferMoney(String login, TransferMoneyDto transferMoneyDto) {
        if (transferMoneyDto.amount() == null || transferMoneyDto.amount() <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        final var fromBalance = accountsClient.getBalance(login);

        if (fromBalance < transferMoneyDto.amount()) {
            throw new IllegalArgumentException("Недостаточно средств");
        }

        accountsClient.transfer(login, transferMoneyDto);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "transfer",
                        transferMoneyDto.amount(),
                        login,
                        transferMoneyDto.login()
                )
        );
    }

    public void transferMoney(Authentication authentication, TransferMoneyDto transferMoneyDto) {
        final var login = getLogin(authentication);

        try {
            performTransferMoney(login, transferMoneyDto);
        } catch (Exception e) {
            transferMetrics.failedTransfer(login, transferMoneyDto.login());

            throw e;
        }
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
