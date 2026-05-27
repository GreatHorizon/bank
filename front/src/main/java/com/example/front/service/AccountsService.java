package com.example.front.service;

import com.example.front.client.AccountsClient;
import com.example.shared.dto.AccountDto;
import com.example.front.model.AccountForTransferModel;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class AccountsService {

    private final AccountsClient accountsClient;

    public AccountsService(AccountsClient accountsClient) {
        this.accountsClient = accountsClient;
    }

    public List<AccountForTransferModel> getAccountsForTransfer(Authentication authentication) {
        return accountsClient.getAccountsForTransfer(authentication)
                .stream().map(dto ->
                        new AccountForTransferModel(
                                dto.login(),
                                dto.lastName() != null ? dto.firstName() + " " + dto.lastName() : dto.firstName()
                        )
                ).toList();
    }

    public AccountDto getAccount(Authentication authentication) {
        return accountsClient.getAccount(authentication);
    }

    public void saveAccount(Authentication authentication, String name, LocalDate birthDate) {
        accountsClient.saveAccount(authentication, name, birthDate);
    }
}
