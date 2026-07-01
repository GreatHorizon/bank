package com.example.accounts.service;

import com.example.accounts.error.AccountNotFoundException;
import com.example.accounts.model.Account;
import com.example.accounts.projection.AccountForTransferProjection;
import com.example.accounts.repository.AccountsRepository;
import com.example.shared.client.NotificationsClient;
import com.example.shared.dto.AccountDto;
import com.example.shared.dto.AccountForTransferDto;
import com.example.shared.dto.CreateAccountDto;
import com.example.shared.dto.TransferMoneyDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountsServiceTest {

    @Mock
    private AccountsRepository accountsRepository;

    @Mock
    private NotificationsClient notificationsClient;

    @InjectMocks
    private AccountsService accountsService;

    @Test
    void createAccountCreatesNewAccount() {
        CreateAccountDto dto = new CreateAccountDto(
                "John Smith",
                LocalDate.now().minusYears(20)
        );

        when(accountsRepository.findByLogin("john")).thenReturn(Optional.empty());

        accountsService.createAccount(dto, "john");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountsRepository).save(captor.capture());

        Account saved = captor.getValue();

        assertEquals("john", saved.getLogin());
        assertEquals("John", saved.getFirstName());
        assertEquals("Smith", saved.getLastName());
        assertEquals(dto.birthDate(), saved.getBirthDate());
        assertEquals(0L, saved.getBalance());

        verify(notificationsClient).sendNotification(any());
    }

    @Test
    void createAccountUpdatesExistingAccountWithoutBalance() {
        Account existing = new Account(
                "john",
                "Old",
                "Name",
                LocalDate.of(1990, 1, 1)
        );

        existing.setId(1L);

        existing.setBalance(999L);

        CreateAccountDto dto = new CreateAccountDto(
                "John Smith",
                LocalDate.now().minusYears(25)
        );

        when(accountsRepository.findByLogin("john")).thenReturn(Optional.of(existing));

        accountsService.createAccount(dto, "john");

        verify(accountsRepository).save(existing);

        assertEquals("John", existing.getFirstName());
        assertEquals("Smith", existing.getLastName());
        assertEquals(dto.birthDate(), existing.getBirthDate());
        assertNotEquals(0L, existing.getBalance());

        verify(notificationsClient).sendNotification(any());
    }

    @Test
    void createAccountThrowsIfUserIsUnder18() {
        CreateAccountDto dto = new CreateAccountDto(
                "Teen User",
                LocalDate.now().minusYears(17)
        );

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountsService.createAccount(dto, "teen")
        );

        assertEquals("User must be at least 18 years old", exception.getMessage());

        verifyNoInteractions(accountsRepository);
        verifyNoInteractions(notificationsClient);
    }

    @Test
    void getAccountByLoginReturnsAccountDto() {
        Account account = new Account(
                "john",
                "John",
                "Smith",
                LocalDate.of(1990, 1, 1)
        );
        account.setBalance(100L);

        when(accountsRepository.findByLogin("john")).thenReturn(Optional.of(account));

        AccountDto result = accountsService.getAccountByLogin("john");

        assertEquals("John", result.firstName());
        assertEquals("Smith", result.lastName());
        assertEquals(LocalDate.of(1990, 1, 1), result.birthDate());
        assertEquals(100L, result.balance());
    }

    @Test
    void getAccountByLoginThrowsIfAccountNotFound() {
        when(accountsRepository.findByLogin("john")).thenReturn(Optional.empty());

        assertThrows(
                AccountNotFoundException.class,
                () -> accountsService.getAccountByLogin("john")
        );
    }

    @Test
    void getAccountsForTransferReturnsOtherAccounts() {
        AccountForTransferProjection anna = new AccountForTransferProjection(
                "anna",
                "Anna",
                "Ivanova"
        );

        AccountForTransferProjection petr = new AccountForTransferProjection(
                "petr",
                "Petr",
                "Petrov"
        );

        when(accountsRepository.findOtherAccountsByLogin("john"))
                .thenReturn(List.of(anna, petr));

        List<AccountForTransferDto> result =
                accountsService.getAccountsForTransfer("john");

        assertEquals(2, result.size());

        assertEquals("anna", result.get(0).login());
        assertEquals("Anna", result.get(0).firstName());
        assertEquals("Ivanova", result.get(0).lastName());

        assertEquals("petr", result.get(1).login());
        assertEquals("Petr", result.get(1).firstName());
        assertEquals("Petrov", result.get(1).lastName());

        verify(notificationsClient).sendNotification(any());
    }

    @Test
    void getBalanceReturnsBalance() {
        when(accountsRepository.findBalanceByLogin("john")).thenReturn(100L);

        Long result = accountsService.getBalance("john");

        assertEquals(100L, result);
        verify(notificationsClient).sendNotification(any());
    }

    @Test
    void putCashIncreasesBalance() {
        when(accountsRepository.deposit("john", 50L)).thenReturn(1);

        accountsService.putCash("john", 50L);

        verify(accountsRepository).deposit("john", 50L);
        verify(accountsRepository, never()).findByLogin(any());
        verify(accountsRepository, never()).save(any());

        verify(notificationsClient).sendNotification(any());
    }

    @Test
    void putCashThrowsIfAmountIsNotPositive() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountsService.putCash("john", 0L)
        );

        assertEquals("Сумма должна быть больше нуля", exception.getMessage());

        verifyNoInteractions(accountsRepository);
        verifyNoInteractions(notificationsClient);
    }

    @Test
    void putCashThrowsIfAccountNotFound() {
        assertThrows(
                AccountNotFoundException.class,
                () -> accountsService.putCash("john", 50L)
        );

        verify(accountsRepository, never()).save(any());
        verifyNoInteractions(notificationsClient);
    }

    @Test
    void getCashDecreasesBalance() {
        Account account = new Account(
                "john",
                "John",
                "Smith",
                LocalDate.of(1990, 1, 1)
        );
        account.setBalance(100L);

        when(accountsRepository.findByLogin("john")).thenReturn(Optional.of(account));
        when(accountsRepository.withdrawIfEnoughMoney("john", 40L)).thenReturn(1);

        accountsService.getCash("john", 40L);

        verify(accountsRepository).withdrawIfEnoughMoney("john", 40L);
        verify(notificationsClient).sendNotification(any());
    }

    @Test
    void getCashThrowsIfAmountIsNotPositive() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountsService.getCash("john", -10L)
        );

        assertEquals("Сумма должна быть больше нуля", exception.getMessage());

        verifyNoInteractions(accountsRepository);
        verifyNoInteractions(notificationsClient);
    }

    @Test
    void getCashThrowsIfNoEnoughBalance() {
        Account account = new Account(
                "john",
                "John",
                "Smith",
                LocalDate.of(1990, 1, 1)
        );

        account.setBalance(100L);

        when(accountsRepository.findByLogin("john")).thenReturn(Optional.of(account));
        when(accountsRepository.withdrawIfEnoughMoney("john", 50000L)).thenReturn(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountsService.getCash("john", 50000L)
        );

        assertEquals("Недостаточно средств", exception.getMessage());

        verify(accountsRepository).withdrawIfEnoughMoney("john", 50000L);
        verifyNoInteractions(notificationsClient);
    }

    @Test
    void transferMovesMoneyBetweenAccounts() {
        Account from = new Account(
                "john",
                "John",
                "Smith",
                LocalDate.of(1990, 1, 1)
        );

        from.setBalance(100L);

        Account to = new Account(
                "anna",
                "Anna",
                "Ivanova",
                LocalDate.of(1995, 1, 1)
        );

        to.setBalance(20L);

        TransferMoneyDto dto = new TransferMoneyDto("anna", 30L);

        when(accountsRepository.findByLogin("john")).thenReturn(Optional.of(from));
        when(accountsRepository.findByLogin("anna")).thenReturn(Optional.of(to));
        when(accountsRepository.withdrawIfEnoughMoney("john", 30L)).thenReturn(1);
        when(accountsRepository.deposit("anna", 30L)).thenReturn(1);

        accountsService.transfer("john", dto);

        verify(accountsRepository).withdrawIfEnoughMoney("john", 30L);

        verify(accountsRepository).deposit("anna", 30L);

        verify(accountsRepository, never()).save(any());
        verify(notificationsClient).sendNotification(any());
    }

    @Test
    void transferThrowsIfNotEnoughMoney() {
        TransferMoneyDto dto = new TransferMoneyDto("anna", 300L);

        Account from = new Account(
                "john",
                "John",
                "Smith",
                LocalDate.of(1990, 1, 1)
        );

        Account to = new Account(
                "anna",
                "Anna",
                "Ivanova",
                LocalDate.of(1995, 1, 1)
        );

        when(accountsRepository.findByLogin("john")).thenReturn(Optional.of(from));
        when(accountsRepository.findByLogin("anna")).thenReturn(Optional.of(to));
        when(accountsRepository.withdrawIfEnoughMoney("john", 300L)).thenReturn(0);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountsService.transfer("john", dto)
        );

        assertEquals("Недостаточно средств", exception.getMessage());

        verify(accountsRepository, never()).save(any());
        verifyNoInteractions(notificationsClient);
    }

    @Test
    void transferThrowsIfNegativeSum() {
        TransferMoneyDto dto = new TransferMoneyDto("anna", -300L);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> accountsService.transfer("john", dto)
        );

        assertEquals("Сумма должна быть больше нуля", exception.getMessage());

        verify(accountsRepository, never()).save(any());
        verifyNoInteractions(notificationsClient);
    }

    @Test
    void transferThrowsIfSourceAccountNotFound() {
        TransferMoneyDto dto = new TransferMoneyDto("anna", 30L);

        when(accountsRepository.findByLogin("john")).thenReturn(Optional.empty());

        assertThrows(
                AccountNotFoundException.class,
                () -> accountsService.transfer("john", dto)
        );

        verify(accountsRepository, never()).save(any());
        verifyNoInteractions(notificationsClient);
    }

    @Test
    void transferThrowsIfTargetAccountNotFound() {
        Account from = new Account(
                "john",
                "John",
                "Smith",
                LocalDate.of(1990, 1, 1)
        );

        from.setBalance(100L);

        TransferMoneyDto dto = new TransferMoneyDto("anna", 30L);

        when(accountsRepository.findByLogin("john")).thenReturn(Optional.of(from));
        when(accountsRepository.findByLogin("anna")).thenReturn(Optional.empty());

        assertThrows(
                AccountNotFoundException.class,
                () -> accountsService.transfer("john", dto)
        );

        verify(accountsRepository, never()).save(any());
        verifyNoInteractions(notificationsClient);
    }
}