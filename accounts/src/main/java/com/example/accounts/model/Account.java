package com.example.accounts.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDate;

@Table(name ="accounts")
public class Account {
    @Id
    private Long id;
    private String login;
    private String firstName;
    private String lastName;
    private LocalDate dateOfBirth;
    private Long balance;

    public Account() {}

    public Account(String login, String firstName, String lastName, LocalDate dateOfBirth) {
        this.login = login;
        this.firstName = firstName;
        this.lastName = lastName;
        this.dateOfBirth = dateOfBirth;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setLogin(String login) {
        this.login = login;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.dateOfBirth = birthDate;
    }

    public void setBalance(Long balance) {
        this.balance = balance;
    }

    public Long getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public LocalDate getBirthDate() {
        return dateOfBirth;
    }

    public Long getBalance() {
        return balance;
    }
}
