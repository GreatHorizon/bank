package com.example.cash.contract;

import com.example.cash.client.AccountsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.contract.stubrunner.spring.AutoConfigureStubRunner;
import org.springframework.cloud.contract.stubrunner.spring.StubRunnerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        "spring.cloud.bootstrap.enabled=false",
        "spring.config.import=optional:",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        "spring.cloud.consul.config.import-check.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "accounts.base-url=http://localhost:8081",
        "spring.autoconfigure.exclude=" +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.DataSourceTransactionManagerAutoConfiguration," +
                "org.springframework.boot.jdbc.autoconfigure.JdbcTemplateAutoConfiguration," +
                "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration"
})
@AutoConfigureStubRunner(
        ids = "com.example:accounts:+:stubs:8081",
        stubsMode = StubRunnerProperties.StubsMode.LOCAL
)
class AccountsClientContractTest {

    @Autowired
    private AccountsClient accountsClient;

    @BeforeEach
    void mockOAuthToken() {
        configureFor("localhost", 8081);

        stubFor(post(urlEqualTo("/token"))
                .willReturn(okJson("""
                        {
                          "access_token": "token",
                          "token_type": "Bearer",
                          "expires_in": 3600
                        }
                        """)));
    }

    @Test
    void getBalanceUsesAccountsContract() {
        Long balance = accountsClient.getBalance("john");

        assertThat(balance).isEqualTo(100L);
    }

    @Test
    void putCashUsesAccountsContract() {
        accountsClient.putCash("john", 50L);
    }

    @Test
    void getCashUsesAccountsContract() {
        accountsClient.getCash("john", 40L);
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
    }
}