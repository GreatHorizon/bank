package com.example.front.client;

import com.example.shared.dto.AccountForTransferDto;
import com.example.shared.dto.AccountDto;
import com.example.shared.dto.CreateAccountDto;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;

@Component
public class AccountsClient {

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final RestClient restClient;

    public AccountsClient(
            OAuth2AuthorizedClientService authorizedClientService,
            RestClient.Builder restClientBuilder
    ) {
        this.authorizedClientService = authorizedClientService;
        this.restClient = restClientBuilder
                .baseUrl("http://localhost:30080")
                .build();
    }

    public AccountDto getAccount(Authentication authentication) {
        String accessToken = getAccessToken(authentication);

        return restClient.get()
                .uri("/accounts")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(AccountDto.class);
    }

    public List<AccountForTransferDto> getAccountsForTransfer(Authentication authentication) {
        String accessToken = getAccessToken(authentication);

        return restClient.get()
                .uri("/accounts/accounts-for-transfer")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .body(new ParameterizedTypeReference<List<AccountForTransferDto>>() {});
    }

    public void saveAccount(Authentication authentication, String name, LocalDate birthDate) {
        String accessToken = getAccessToken(authentication);

        final var dto = new CreateAccountDto(name, birthDate);

        restClient.post()
                .uri("/accounts")
                .headers(headers -> headers.setBearerAuth(accessToken))
                .body(dto)
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