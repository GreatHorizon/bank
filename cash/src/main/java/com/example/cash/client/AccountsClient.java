package com.example.cash.client;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class AccountsClient {

    private final RestClient restClient;

    public AccountsClient(@Qualifier("accountsRestClient") RestClient restClient) {
        this.restClient = restClient;
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

    public void putCash(String login, Long amount) {
        restClient.put()
                .uri(uriBuilder -> uriBuilder
                        .path("/accounts/balance")
                        .queryParam("amount", amount)
                        .queryParam("login", login)
                        .build())
                .retrieve()
                .toBodilessEntity();
    }

    public void getCash(String login, Long amount) {
        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/accounts/balance")
                        .queryParam("amount", amount)
                        .queryParam("login", login)
                        .build())
                .retrieve()
                .toBodilessEntity();
    }
}