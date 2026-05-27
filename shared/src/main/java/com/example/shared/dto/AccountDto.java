package com.example.shared.dto;

import java.time.LocalDate;

public record AccountDto(
        String firstName, String lastName, LocalDate birthDate, Long balance
) {
}