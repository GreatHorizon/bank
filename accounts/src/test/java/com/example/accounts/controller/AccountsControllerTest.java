package com.example.accounts.controller;

import com.example.accounts.model.Account;
import com.example.accounts.repository.AccountsRepository;
import com.example.shared.client.NotificationsClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.consul.enabled=false",
                "spring.cloud.consul.config.enabled=false",
                "spring.cloud.consul.config.import-check.enabled=false",
                "spring.cloud.discovery.enabled=false"
        }
)
@AutoConfigureMockMvc
class AccountsControllerIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("accounts_test")
            .withUsername("test")
            .withPassword("test");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountsRepository accountsRepository;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @MockitoBean
    private NotificationsClient notificationsClient;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", postgres::getDriverClassName);
        registry.add("spring.liquibase.enabled", () -> "true");
        registry.add("spring.liquibase.change-log",
                () -> "classpath:db.changelog/db.changelog-master.yaml");
        registry.add("spring.liquibase.url", postgres::getJdbcUrl);
        registry.add("spring.liquibase.user", postgres::getUsername);
        registry.add("spring.liquibase.password", postgres::getPassword);
        registry.add("spring.cloud.consul.enabled", () -> "false");
        registry.add("spring.cloud.consul.config.enabled", () -> "false");
        registry.add("spring.cloud.discovery.enabled", () -> "false");
    }

    private static Account account(String login, String firstName, String lastName, Long balance) {
        Account account = new Account();
        account.setLogin(login);
        account.setFirstName(firstName);
        account.setLastName(lastName);
        account.setBalance(balance);
        return account;
    }

    @BeforeEach
    void setUp() {
        accountsRepository.deleteAll();

        accountsRepository.save(account("john", "John", "Doe", 100L));
        accountsRepository.save(account("alice", "Alice", "Smith", 50L));

        Jwt decodedJwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "john")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtDecoder.decode(anyString())).thenReturn(decodedJwt);
    }

    @Test
    void getAccountReturnsCurrentUserAccount() throws Exception {
        mockMvc.perform(get("/accounts")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "john"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.balance").value(100));
    }

    @Test
    void createAccountCreatesNewAccountForCurrentUser() throws Exception {
        String body = """
                {
                  "name": "Bob Brown",
                  "birthDate": "2000-01-01"
                }
                """;

        mockMvc.perform(post("/accounts")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "bob")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        Account saved = accountsRepository.findByLogin("bob").orElseThrow();

        assertThat(saved.getFirstName()).isEqualTo("Bob");
        assertThat(saved.getLastName()).isEqualTo("Brown");
        assertThat(saved.getBirthDate())
                .isEqualTo(java.time.LocalDate.of(2000, 1, 1));
        assertThat(saved.getBalance()).isEqualTo(0L);

    }

    @Test
    void getAccountsForTransferDoesNotReturnCurrentUser() throws Exception {
        mockMvc.perform(get("/accounts/accounts-for-transfer")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "john"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].login").value("alice"));
    }

    @Test
    void getBalanceWithAccountsRoleReturnsBalance() throws Exception {
        mockMvc.perform(get("/accounts/balance")
                        .param("login", "john")
                        .with(jwt().authorities(() -> "accounts_role")))
                .andExpect(status().isOk())
                .andExpect(content().string("100"));
    }

    @Test
    void putCashWithAccountsRoleIncreasesBalance() throws Exception {
        mockMvc.perform(put("/accounts/balance")
                        .param("login", "john")
                        .param("amount", "25")
                        .with(jwt().authorities(() -> "accounts_role")))
                .andExpect(status().isOk());

        assertThat(accountsRepository.findByLogin("john").orElseThrow().getBalance())
                .isEqualTo(125L);
    }

    @Test
    void getCashWithAccountsRoleDecreasesBalance() throws Exception {
        mockMvc.perform(post("/accounts/balance")
                        .param("login", "john")
                        .param("amount", "30")
                        .with(jwt().authorities(() -> "accounts_role")))
                .andExpect(status().isOk());

        assertThat(accountsRepository.findByLogin("john").orElseThrow().getBalance())
                .isEqualTo(70L);
    }

    @Test
    void transferWithAccountsRoleMovesMoneyBetweenAccounts() throws Exception {

        String body = """
                {
                  "login": "alice",
                  "amount": 40
                }
                """;

        mockMvc.perform(post("/accounts/transfer")
                        .param("login", "john")
                        .with(jwt().authorities(() -> "accounts_role"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());

        assertThat(accountsRepository.findByLogin("john").orElseThrow().getBalance())
                .isEqualTo(60L);

        assertThat(accountsRepository.findByLogin("alice").orElseThrow().getBalance())
                .isEqualTo(90L);

    }

    @Test
    void protectedEndpointWithoutAccountsRoleReturnsForbidden() throws Exception {
        mockMvc.perform(get("/accounts/balance")
                        .param("login", "john")
                        .with(jwt()))
                .andExpect(status().isForbidden());
    }

    @Test
    void putCashWithZeroAmountDoesNotChangeBalance() throws Exception {
        mockMvc.perform(put("/accounts/balance")
                        .param("login", "john")
                        .param("amount", "0")
                        .with(jwt().authorities(() -> "accounts_role")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Сумма должна быть больше нуля"));

        assertThat(
                accountsRepository.findByLogin("john").orElseThrow().getBalance()
        ).isEqualTo(100L);

        verifyNoInteractions(notificationsClient);
    }

    @Test
    void getCashWithInsufficientBalanceDoesNotChangeBalance() throws Exception {
        mockMvc.perform(post("/accounts/balance")
                        .param("login", "john")
                        .param("amount", "1000")
                        .with(jwt().authorities(() -> "accounts_role")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("Недостаточно средств"));

        assertThat(
                accountsRepository.findByLogin("john").orElseThrow().getBalance()
        ).isEqualTo(100L);

        verifyNoInteractions(notificationsClient);
    }

    @Test
    void createAccountForUnderageUserDoesNotCreateAccount() throws Exception {
        String body = """
                {
                  "name": "Young User",
                  "birthDate": "2015-01-01"
                }
                """;

        mockMvc.perform(post("/accounts")
                        .with(jwt().jwt(jwt -> jwt.claim("preferred_username", "young")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("User must be at least 18 years old"));

        assertThat(accountsRepository.findByLogin("young"))
                .isEmpty();

        verifyNoInteractions(notificationsClient);
    }
}
