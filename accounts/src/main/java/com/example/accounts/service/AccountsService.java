package com.example.accounts.service;

import com.example.accounts.error.AccountNotFoundException;
import com.example.accounts.model.Account;
import com.example.accounts.repository.AccountsRepository;
import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;

@Service
public class AccountsService {

    private final AccountsRepository accountsRepository;
    private final NotificationsClient notificationsClient;

    private static final Logger log = LoggerFactory.getLogger(AccountsService.class);

    public AccountsService(AccountsRepository accountsRepository, NotificationsClient notificationsClient) {
        this.accountsRepository = accountsRepository;
        this.notificationsClient = notificationsClient;
    }

    @Transactional
    public void createAccount(CreateAccountDto createAccountDto, String login) {
        LocalDate today = LocalDate.now();

        int years = Period.between(createAccountDto.birthDate(), today).getYears();

        if (years < 18) {
            log.warn("Invalid age: years={}", years);

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

        final var existingAccount = account.getId() != null;

        account.setFirstName(nameParts.firstName());
        account.setLastName(nameParts.lastName());
        account.setBirthDate(createAccountDto.birthDate());

        if (existingAccount) {
            log.info("Account profile updated without balance reset: login={}, balance={}",
                    login, account.getBalance());

        } else {
            log.info("Account created: login={}", login);
            account.setBalance(0L);
        }

        accountsRepository.save(account);


        log.info("Sending cash createAccount notification: login={}", login);

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
                                model.login(),
                                model.firstName(),
                                model.lastName()
                        )
                ).toList();


        log.info("Sending cash getAccountsForTransfer notification: login={}", login);

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

        log.info("Sending cash getBalance notification: login={}", login);


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
            log.warn("Invalid put cash amount: login={}, amount={}", login, amount);
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        final var depositedRows = accountsRepository.deposit(login, amount);

        if (depositedRows == 0) {
            log.warn("Account not found for transfer: login={}", login);

            throw new AccountNotFoundException(login);
        }

        log.info("Sending cash putCash notification: login={}", login);

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
            log.warn("Invalid get cash amount: login={}, amount={}", login, amount);
            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        if (accountsRepository.findByLogin(login).isEmpty()) {
            log.error("Invalid account in get cash: login={}, amount={}", login, amount);

            throw new AccountNotFoundException(login);
        }

        final var withdrawnRows = accountsRepository.withdrawIfEnoughMoney(login, amount);

        if (withdrawnRows == 0) {
            log.warn("Invalid get cash amount: login={}, amount={}", login, amount);

            throw new IllegalArgumentException("Недостаточно средств");
        }

        log.info("Sending cash getCash notification: login={}", login);

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
            log.warn("Invalid transfer amount: login={}, amount={}", transferMoneyDto.login(), transferMoneyDto.amount());

            throw new IllegalArgumentException("Сумма должна быть больше нуля");
        }

        if (accountsRepository.findByLogin(fromLogin).isEmpty()) {
            log.warn("Account not found for transfer: fromLogin={}", fromLogin);

            throw new AccountNotFoundException(fromLogin);
        }

        if (accountsRepository.findByLogin(toLogin).isEmpty()) {
            log.warn("Account not found for transfer: toLogin={}", toLogin);

            throw new AccountNotFoundException(toLogin);
        }

        final var withdrawnRows = accountsRepository.withdrawIfEnoughMoney(fromLogin, transferAmount);

        if (withdrawnRows == 0) {
            log.warn("Not enough money: transferAmount={}", transferAmount);

            throw new IllegalArgumentException("Недостаточно средств");
        }

        final var depositedRows = accountsRepository.deposit(toLogin, transferAmount);

        if (depositedRows == 0) {
            log.warn("Account not found for transfer: toLogin={}", toLogin);

            throw new AccountNotFoundException(toLogin);
        }

        log.info("Sending cash transfer notification: login={}", transferMoneyDto.login());

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
