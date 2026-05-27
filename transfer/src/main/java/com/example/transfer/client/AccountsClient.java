package com.example.transfer.client;

import com.example.shared.dto.TransferMoneyDto;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AccountsClient {

    private final RestClient restClient;

    public AccountsClient(@Qualifier("accountsRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void transfer(String login, TransferMoneyDto transferMoneyDto) {
        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/accounts/transfer")
                        .queryParam("login", login)
                        .build())
                .body(transferMoneyDto)
                .retrieve()
                .toBodilessEntity();
    }

    public Long getBalance(String login) {
        return restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/accounts/balance")
                        .queryParam("login", login)
                        .build())
                .retrieve()
                .body(Long.class);
    }
}