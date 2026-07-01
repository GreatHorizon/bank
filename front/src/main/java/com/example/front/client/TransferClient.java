package com.example.front.client;

import com.example.shared.dto.TransferMoneyDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class TransferClient {
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestClient restClient;

    public TransferClient(
            OAuth2AuthorizedClientService authorizedClientService,
            RestClient.Builder restClientBuilder,
            @Value("${app.gateway.base-url}")
            String gatewayBaseUrl
    ) {
        this.authorizedClientService = authorizedClientService;
        this.restClient = restClientBuilder
                .baseUrl(gatewayBaseUrl)
                .build();
    }

    public void transfer(Authentication authentication, TransferMoneyDto transferMoneyDto) {
        final var accessToken = getAccessToken(authentication);

        restClient.post()
                .uri("/api/transfer")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(transferMoneyDto)
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
