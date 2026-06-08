package com.example.cash.controller;

import com.example.cash.client.AccountsClient;
import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.NotificationDto;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.cloud.bootstrap.enabled=false",
        "spring.config.import=optional:",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        "spring.cloud.consul.config.import-check.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration," +
                "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration",
        "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/test-issuer"

})
@AutoConfigureMockMvc
class CashControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AccountsClient accountsClient;

    @MockitoBean
    private NotificationsClient notificationsClient;

    @Test
    void putCashAddsMoneyToAccountAndSendsNotification() throws Exception {
        mockMvc.perform(put("/api/cash")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "john")))
                        .param("amount", "50")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(accountsClient).putCash("john", 50L);
        verify(notificationsClient).sendNotification(any(NotificationDto.class));
    }

    @Test
    void getCashWithdrawsMoneyWhenBalanceIsEnoughAndSendsNotification() throws Exception {
        when(accountsClient.getBalance("john")).thenReturn(100L);

        mockMvc.perform(post("/api/cash")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "john")))
                        .param("amount", "40")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());

        verify(accountsClient).getBalance("john");
        verify(accountsClient).getCash("john", 40L);
        verify(notificationsClient).sendNotification(any(NotificationDto.class));
    }

    @Test
    void getCashReturnsServerErrorWhenAmountGreaterThanBalance() throws Exception {
        when(accountsClient.getBalance("john")).thenReturn(30L);

        mockMvc.perform(post("/api/cash")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "john")))
                        .param("amount", "40")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError());

        verify(accountsClient).getBalance("john");
    }

    @TestConfiguration
    static class OAuth2TestConfig {
        @Bean
        @Primary
        ClientRegistrationRepository clientRegistrationRepository() {
            ClientRegistration registration = ClientRegistration
                    .withRegistrationId("cash-service")
                    .clientId("test-client")
                    .clientSecret("test-secret")
                    .tokenUri("http://localhost:8081/token")
                    .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                    .build();

            return new InMemoryClientRegistrationRepository(registration);
        }

        @Bean
        @Primary
        OAuth2AuthorizedClientManager authorizedClientManager() {
            return mock(OAuth2AuthorizedClientManager.class);
        }
    }
}