package com.example.accounts.repository;

import com.example.accounts.model.Account;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AccountsRepository extends CrudRepository<Account, Long> {
    Optional<Account> findByLogin(String login);

    @Query("SELECT login, first_name, last_name FROM accounts a WHERE a.login <> :login")
    List<Account> findOtherAccountsByLogin(@Param("login") String login);



    @Query("SELECT balance FROM accounts a WHERE a.login = :login")
    Long findBalanceByLogin(@Param("login") String login);
}