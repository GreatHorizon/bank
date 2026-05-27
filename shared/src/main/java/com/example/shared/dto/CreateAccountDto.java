package com.example.shared.dto;

import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record CreateAccountDto(
        @NotBlank String name,
        LocalDate birthDate
) {
}