package com.example.front.client;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class CashClient {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestClient restClient;

    public CashClient(OAuth2AuthorizedClientService authorizedClientService) {
        this.authorizedClientService = authorizedClientService;
        this.restClient = RestClient.builder()
                .baseUrl("http://localhost:8085")
                .build();
    }


    public void getCash(Authentication authentication, int amount) {
        String accessToken = getAccessToken(authentication);

        restClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/cash")
                        .queryParam("amount", amount)
                        .build()
                ).headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .toBodilessEntity();
    }

    public void putCash(Authentication authentication, int amount) {
        String accessToken = getAccessToken(authentication);

        restClient.put()

                .uri(uriBuilder -> uriBuilder

                        .path("/api/cash")

                        .queryParam("amount", amount)

                        .build())

                .headers(headers -> headers.setBearerAuth(accessToken))

                .retrieve()

                .toBodilessEntity();

    }

    private String getAccessToken(Authentication authentication) {
        OAuth2AuthorizedClient client = authorizedClientService.loadAuthorizedClient(
                "keycloak",
                authentication.getName()
        );

        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("No OAuth2 access token found for current user");
        }

        return client.getAccessToken().getTokenValue();
    }
}