package com.example.accounts.error;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException(String login) {

        super("Account not found for login: " + login);

    }

}