package com.example.accounts.controller;

import com.example.accounts.service.AccountsService;
import com.example.shared.dto.AccountDto;
import com.example.shared.dto.AccountForTransferDto;
import com.example.shared.dto.CreateAccountDto;
import com.example.shared.dto.TransferMoneyDto;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController()
@RequestMapping("/accounts")
public class AccountsController {
    private final AccountsService accountsService;

    public AccountsController(AccountsService accountsService) {
        this.accountsService = accountsService;
    }

    @GetMapping
    AccountDto getAccount(JwtAuthenticationToken authentication) {
        final String login = authentication.getToken().getClaimAsString("preferred_username");

        return accountsService.getAccountByLogin(login);
    }

    @PostMapping
    void createAccount(JwtAuthenticationToken authentication, @Valid @RequestBody CreateAccountDto createAccountDto) {
        final String login = authentication.getToken().getClaimAsString("preferred_username");

        accountsService.createAccount(createAccountDto, login);
    }

    @GetMapping("/accounts-for-transfer")
    List<AccountForTransferDto> getAccountsForTransfer(JwtAuthenticationToken authentication) {
        final String login = authentication.getToken().getClaimAsString("preferred_username");

        return accountsService.getAccountsForTransfer(login);
    }

    @GetMapping("/balance")
    @PreAuthorize("hasAuthority('accounts_role')")
    Long getBalance(@RequestParam String login) {
        return accountsService.getBalance(login);
    }

    @PutMapping("/balance")
    @PreAuthorize("hasAuthority('accounts_role')")
    void putCash(@RequestParam Long amount, @RequestParam String login) {
        accountsService.putCash(login, amount);
    }

    @PostMapping("/balance")
    @PreAuthorize("hasAuthority('accounts_role')")
    void getCash(@RequestParam String login, @RequestParam Long amount) {
        accountsService.getCash(login, amount);
    }

    @PostMapping("/transfer")
    @PreAuthorize("hasAuthority('accounts_role')")
    void transferAccount(@RequestParam String login, @RequestBody TransferMoneyDto transferMoneyDto) {
        accountsService.transfer(login, transferMoneyDto);
    }
}
