package com.example.accounts.service;

import com.example.accounts.error.AccountNotFoundException;
import com.example.accounts.model.AccountModel;
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

        AccountModel accountModel = accountsRepository.findByLogin(login)
                .orElseGet(() -> new AccountModel(
                                login,
                                nameParts.firstName(),
                                nameParts.lastName(),
                                createAccountDto.birthDate()
                        )
                );

        accountModel.setFirstName(nameParts.firstName());
        accountModel.setLastName(nameParts.lastName());
        accountModel.setBirthDate(createAccountDto.birthDate());
        accountModel.setBalance(0L);

        accountsRepository.save(accountModel);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "createAccount",
                        "accounts-service",
                        null,
                        login,
                        null
                )
        );
    }

    public AccountDto getAccountByLogin(String login) {
        return accountsRepository.findByLogin(login)
                .map((model) -> new AccountDto(model.getFirstName(), model.getLastName(), model.getbirthDate(), model.getBalance()))
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
                        "accounts-service",
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
                        "accounts-service", null, login,
                        null
                )
        );

        return balance;
    }

    @Transactional
    public void putCash(String login, Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        AccountModel account = accountsRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException(login));

        final var newBalance = account.getBalance() + amount;

        account.setBalance(newBalance);

        accountsRepository.save(account);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "putCash",
                        "accounts-service", null, login,
                        null
                )
        );
    }

    @Transactional
    public void getCash(String login, Long amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        AccountModel account = accountsRepository.findByLogin(login)
                .orElseThrow(() -> new AccountNotFoundException(login));

        account.setBalance(account.getBalance() - amount);

        accountsRepository.save(account);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "getCash",
                        "accounts-service",
                        null,
                        login,
                        null
                )
        );
    }

    @Transactional
    public void transfer(String fromLogin, TransferMoneyDto transferMoneyDto) {
        if (accountsRepository.findBalanceByLogin(fromLogin) < transferMoneyDto.amount()) {
            throw new IllegalArgumentException("Недостаточно средств");
        }

        final var accountFromOptional = accountsRepository.findByLogin(fromLogin);
        final var accountToOptional = accountsRepository.findByLogin(transferMoneyDto.login());

        if (accountFromOptional.isEmpty()) {
            throw new AccountNotFoundException(fromLogin);
        }

        if (accountToOptional.isEmpty()) {
            throw new AccountNotFoundException(transferMoneyDto.login());
        }

        final var accountFrom = accountFromOptional.get();
        final var accountTo = accountToOptional.get();

        accountFrom.setBalance(accountFrom.getBalance() - transferMoneyDto.amount());
        accountTo.setBalance(accountTo.getBalance() + transferMoneyDto.amount());

        accountsRepository.save(accountFrom);
        accountsRepository.save(accountTo);

        notificationsClient.sendNotification(
                new NotificationDto(
                        "getCash",
                        "accounts-service",
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
