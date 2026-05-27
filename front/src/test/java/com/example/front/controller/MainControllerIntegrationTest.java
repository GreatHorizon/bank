package com.example.front.controller;

import com.example.front.client.AccountsClient;
import com.example.front.client.CashClient;
import com.example.front.client.TransferClient;
import com.example.front.dto.CashAction;
import com.example.shared.dto.AccountDto;
import com.example.shared.dto.AccountForTransferDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "spring.cloud.bootstrap.enabled=false",
        "spring.config.import=optional:",
        "spring.cloud.consul.enabled=false",
        "spring.cloud.consul.config.enabled=false",
        "spring.cloud.consul.config.import-check.enabled=false",
        "spring.cloud.discovery.enabled=false",
        "spring.security.oauth2.client.registration.front_client.client-id=test-client",
        "spring.security.oauth2.client.registration.front_client.client-secret=test-secret",
        "spring.security.oauth2.client.registration.front_client.authorization-grant-type=authorization_code",
        "spring.security.oauth2.client.registration.front_client.redirect-uri=http://localhost/login/oauth2/code/front_client",
        "spring.security.oauth2.client.registration.front_client.scope=openid",
        "spring.security.oauth2.client.provider.front_client.authorization-uri=http://localhost/oauth2/authorize",
        "spring.security.oauth2.client.provider.front_client.token-uri=http://localhost/oauth2/token",
        "spring.security.oauth2.client.provider.front_client.user-info-uri=http://localhost/userinfo",
        "spring.security.oauth2.client.provider.front_client.user-name-attribute=sub"
})
@AutoConfigureMockMvc
@Import(MainControllerIntegrationTest.TestOAuth2Config.class)
class MainControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuth2AuthorizedClientService authorizedClientService;

    @MockitoBean
    private AccountsClient accountsClient;

    @MockitoBean
    private CashClient cashClient;

    @MockitoBean
    private TransferClient transferClient;

    @MockitoBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockitoBean
    private OAuth2AuthorizedClientRepository authorizedClientRepository;

    static class TestOAuth2Config {
        // empty config; @MockitoBean above overrides OAuth2 beans
    }

    @BeforeEach
    void setUp() {
        when(accountsClient.getAccount(any(Authentication.class)))
                .thenReturn(new AccountDto("Ivan", "Ivanov", LocalDate.of(2000, 1, 1), 1_000L));
        when(accountsClient.getAccountsForTransfer(any(Authentication.class)))
                .thenReturn(List.of(new AccountForTransferDto("petr", "Petr", "Petrov")));
    }

    @Test
    void indexRedirectsToAccount() throws Exception {
        mockMvc.perform(get("/")
                        .with(oauth2Login()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/account"));

        verifyNoInteractions(accountsClient, cashClient, transferClient);
    }

    @Test
    void accountPageReturnsMainView() throws Exception {
        mockMvc.perform(get("/account")
                        .with(oauth2Login()))
                .andExpect(status().isOk())
                .andExpect(view().name("main"))
                .andExpect(model().attribute("name", "Ivan Ivanov"))
                .andExpect(model().attribute("birthdate", LocalDate.of(2000, 1, 1)))
                .andExpect(model().attribute("sum", 1_000L))
                .andExpect(model().attributeExists("accounts"));

        verify(accountsClient).getAccount(any(Authentication.class));
        verify(accountsClient).getAccountsForTransfer(any(Authentication.class));
    }

    @Test
    void accountPageRedirectsToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/account"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void saveAccountReturnsMainView() throws Exception {
        mockMvc.perform(post("/account")
                        .with(oauth2Login())
                        .with(csrf())
                        .param("name", "Ivan Ivanov")
                        .param("birthdate", "2000-01-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("main"));

        verify(accountsClient).saveAccount(
                any(Authentication.class),
                eq("Ivan Ivanov"),
                eq(LocalDate.of(2000, 1, 1))
        );
        verify(accountsClient).getAccount(any(Authentication.class));
        verify(accountsClient).getAccountsForTransfer(any(Authentication.class));
    }

    @Test
    void getCashReturnsMainView() throws Exception {
        mockMvc.perform(post("/cash")
                        .with(oauth2Login())
                        .with(csrf())
                        .param("value", "100")
                        .param("action", CashAction.GET.name()))
                .andExpect(status().isOk())
                .andExpect(view().name("main"))
                .andExpect(model().attribute("info", "Снято 100 рублей"));

        verify(cashClient).getCash(any(Authentication.class), eq(100));
        verify(cashClient, never()).putCash(any(Authentication.class), anyInt());
        verify(accountsClient).getAccount(any(Authentication.class));
        verify(accountsClient).getAccountsForTransfer(any(Authentication.class));
    }

    @Test
    void putCashReturnsMainView() throws Exception {
        mockMvc.perform(post("/cash")
                        .with(oauth2Login())
                        .with(csrf())
                        .param("value", "200")
                        .param("action", CashAction.PUT.name()))
                .andExpect(status().isOk())
                .andExpect(view().name("main"))
                .andExpect(model().attribute("info", "Внесено 200 рублей"));

        verify(cashClient).putCash(any(Authentication.class), eq(200));
        verify(cashClient, never()).getCash(any(Authentication.class), anyInt());
        verify(accountsClient).getAccount(any(Authentication.class));
        verify(accountsClient).getAccountsForTransfer(any(Authentication.class));
    }

    @Test
    void transferReturnsMainView() throws Exception {
        mockMvc.perform(post("/transfer")
                        .with(oauth2Login())
                        .with(csrf())
                        .param("login", "petr")
                        .param("value", "50"))
                .andExpect(status().isOk())
                .andExpect(view().name("main"))
                .andExpect(model().attribute("info", "Средства отправлены"));

        verify(transferClient).transfer(
                any(Authentication.class),
                argThat(dto -> "petr".equals(dto.login()) && Long.valueOf(50L).equals(dto.amount()))
        );
        verify(accountsClient).getAccount(any(Authentication.class));
        verify(accountsClient).getAccountsForTransfer(any(Authentication.class));
    }
}
