package com.example.transfer.controller;

import com.example.shared.dto.TransferMoneyDto;
import com.example.transfer.service.TransferService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cloud.bootstrap.enabled=false",
        "spring.config.import=optional:",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        "spring.cloud.consul.config.import-check.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer"
})
@AutoConfigureMockMvc
class TransferControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TransferService transferService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void transferMoneyCallsTransferService() throws Exception {
        mockMvc.perform(post("/api/transfer")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "john")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "login": "petr",
                                  "amount": 30
                                }
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<TransferMoneyDto> dtoCaptor =
                ArgumentCaptor.forClass(TransferMoneyDto.class);

        verify(transferService).transferMoney(any(), dtoCaptor.capture());

        TransferMoneyDto dto = dtoCaptor.getValue();

        assertThat(dto.login()).isEqualTo("petr");
        assertThat(dto.amount()).isEqualTo(30L);
    }

    @Test
    void transferMoneyReturnsBadRequestWhenAmountIsInvalid() throws Exception {
        doThrow(new IllegalArgumentException("Сумма должна быть больше нуля"))
                .when(transferService)
                .transferMoney(any(), any(TransferMoneyDto.class));

        mockMvc.perform(post("/api/transfer")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "john")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "login": "petr",
                                  "amount": 0
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferMoneyReturnsBadRequestWhenBalanceIsNotEnough() throws Exception {
        doThrow(new IllegalArgumentException("Недостаточно средств"))
                .when(transferService)
                .transferMoney(any(), any(TransferMoneyDto.class));

        mockMvc.perform(post("/api/transfer")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "john")))
                        .contentType("application/json")
                        .content("""
                                {
                                  "login": "petr",
                                  "amount": 1000
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void transferMoneyReturnsUnauthorizedWithoutJwt() throws Exception {
        mockMvc.perform(post("/api/transfer")
                        .contentType("application/json")
                        .content("""
                                {
                                  "login": "petr",
                                  "amount": 30
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @TestConfiguration
    static class OAuth2TestConfig {

        @Bean
        @Primary
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration
                    .withRegistrationId("keycloak")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .tokenUri("http://localhost:8081/token")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }

        @Bean
        @Primary
        OAuth2AuthorizedClientService authorizedClientService(
                ClientRegistrationRepository clientRegistrationRepository
        ) {
            return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
        }
    }
}