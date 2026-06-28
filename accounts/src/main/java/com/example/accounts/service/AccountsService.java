package com.example.accounts.service;

import com.example.accounts.error.AccountNotFoundException;
import com.example.accounts.model.Account;
import com.example.accounts.repository.AccountsRepository;
import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class AccountsService {

    private final AccountsRepository accountsRepository;
    private final NotificationsClient notificationsClient;

    public AccountsService(AccountsRepository accountsRepository, NotificationsClient notificationsClient) {
        this.accountsRepository = accountsRepository;
        this.notificationsClient = notificationsClient;
    }

    @Transactional
    public void createAccount(CreateAccountDto createAccountDto, String login) {
        LocalDate today = LocalDate.now();

        int years = Period.between(createAccountDto.birthDate(), today).getYears();

        if (years < 18) {
            throw new IllegalArgumentException("User must be at least 18 years old");
        }


        NameParts nameParts = parseName(createAccountDto.name());

        Account account = accountsRepository.findByLogin(login)
                .orElseGet(() -> new Account(
                                login,
                                nameParts.firstName(),
                                nameParts.lastName(),
                                createAccountDto.birthDate()
                        )
                );

        account.setFirstName(nameParts.firstName());
        account.setLastName(nameParts.lastName());
        account.setBirthDate(createAccountDto.birthDate());
        account.setBalance(0L);

        accountsRepository.save(account);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "createAccount",
                        null,
                        login,
                        null
                )
        );
    }

    public AccountDto getAccountByLogin(String login) {
        return accountsRepository.findByLogin(login)
                .map((model) -> new AccountDto(model.getFirstName(), model.getLastName(), model.getBirthDate(), model.getBalance()))
                .orElseThrow(() -> new AccountNotFoundException(login));
    }

    public List<AccountForTransferDto> getAccountsForTransfer(String login) {
        final var accounts = accountsRepository
                .findOtherAccountsByLogin(login)
                .stream()
                .map(model ->
                        new AccountForTransferDto(
                                model.getLogin(),
                                model.getFirstName(),
                                model.getLastName()
                        )
                ).toList();

        notificationsClient.sendNotification(
                new NotificationDto(
                        "getAccountsForTransfer",
                        null, login,
                        null
                )
        );

        return accounts;
    }

    public Long getBalance(String login) {
        final var balance = accountsRepository.findBalanceByLogin(login);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "balance",
                        null,
                        login,
                        null
                )
        );

        return balance;
    }

    @Transactional
    public void putCash(String login, Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        Account account = accountsRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException(login));

        final var newBalance = account.getBalance() + amount;

        account.setBalance(newBalance);

        accountsRepository.save(account);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "putCash",
                        null,
                        login,
                        null
                )
        );
    }

    @Transactional
    public void getCash(String login, Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        if (accountsRepository.findByLogin(login).isEmpty()) {
            throw new AccountNotFoundException(login);
        }

        final var withdrawnRows = accountsRepository.withdrawIfEnoughMoney(login, amount);

        if (withdrawnRows == 0) {
            throw new IllegalArgumentException("Недостаточно средств");
        }

        notificationsClient.sendNotification(
                new NotificationDto(
                        "getCash",
                        null,
                        login,
                        null
                )
        );
    }

    @Transactional
    public void transfer(String fromLogin, TransferMoneyDto transferMoneyDto) {
        final var transferAmount = transferMoneyDto.amount();
        final var toLogin = transferMoneyDto.login();

        if (transferAmount == null || transferAmount <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        if (accountsRepository.findByLogin(fromLogin).isEmpty()) {
            throw new AccountNotFoundException(fromLogin);
        }

        if (accountsRepository.findByLogin(toLogin).isEmpty()) {
            throw new AccountNotFoundException(toLogin);
        }

        final var withdrawnRows = accountsRepository.withdrawIfEnoughMoney(fromLogin, transferAmount);

        if (withdrawnRows == 0) {
            throw new IllegalArgumentException("Недостаточно средств");
        }

        final var depositedRows = accountsRepository.deposit(toLogin, transferAmount);

        if (depositedRows == 0) {
            throw new AccountNotFoundException(toLogin);
        }

        notificationsClient.sendNotification(
                new NotificationDto(
                        "transfer",
                        transferMoneyDto.amount(),
                        fromLogin,
                        transferMoneyDto.login()
                )
        );
    }

    private NameParts parseName(String name) {
        if (name == null || name.isBlank()) {
            return new NameParts(null, null);
        }

        String[] parts = name.trim().split("\\s+", 2);

        String firstName = parts[0];
        String lastName = parts.length > 1 ? parts[1] : null;

        return new NameParts(firstName, lastName);
    }

    private record NameParts(String firstName, String lastName) {
    }
}
