package com.example.accounts.contract;

import com.example.accounts.service.AccountsService;
import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.AccountDto;
import com.example.shared.dto.AccountForTransferDto;
import io.restassured.module.mockmvc.RestAssuredMockMvc;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.consul.enabled=false",
                "spring.cloud.consul.config.enabled=false",
                "spring.cloud.consul.config.import-check.enabled=false",
                "spring.cloud.discovery.enabled=false",

                // главное: контрактам не нужна настоящая БД
                "spring.autoconfigure.exclude=" +
                        "org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration," +
                        "org.springframework.boot.liquibase.autoconfigure.LiquibaseAutoConfiguration," +
                        "org.springframework.boot.data.jdbc.autoconfigure.DataJdbcRepositoriesAutoConfiguration"
        }
)
@AutoConfigureMockMvc
public abstract class ContractBaseTest {

    @Autowired
    protected MockMvc mockMvc;

    @MockitoBean
    protected AccountsService accountsService;

    @MockitoBean
    protected NotificationsClient notificationsClient;

    @MockitoBean
    protected JwtDecoder jwtDecoder;

    @MockitoBean
    protected OAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    protected ClientRegistrationRepository clientRegistrationRepository;

    @BeforeEach
    void setup() {
        RestAssuredMockMvc.mockMvc(mockMvc);

        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .claim("preferred_username", "john")
                .claim("resource_access", Map.of(
                        "accounts-service", Map.of(
                                "roles", List.of("accounts_role")
                        )
                ))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();

        when(jwtDecoder.decode(anyString())).thenReturn(jwt);

        when(accountsService.getAccountByLogin("john"))
                .thenReturn(new AccountDto(
                        "John",
                        "Smith",
                        LocalDate.of(1990, 1, 1),
                        100L
                ));

        when(accountsService.getAccountsForTransfer("john"))
                .thenReturn(List.of(
                        new AccountForTransferDto(
                                "anna",
                                "Anna",
                                "Ivanova"
                        ),
                        new AccountForTransferDto(
                                "petr",
                                "Petr",
                                "Petrov"
                        )
                ));

        when(accountsService.getBalance("john"))
                .thenReturn(100L);

        doNothing()
                .when(accountsService)
                .putCash("john", 50L);

        doNothing()
                .when(accountsService)
                .getCash("john", 40L);
    }
}