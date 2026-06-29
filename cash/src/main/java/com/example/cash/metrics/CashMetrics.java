package com.example.cash.metrics;

import io.micrometer.core.instrument.Counter;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component

public class CashMetrics {

    private final MeterRegistry meterRegistry;

    public CashMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void failedWithdrawal(String login) {
        Counter.builder("cash_withdrawal_failed_total")
                .description("Number of failed cash withdrawal attempts")
                .tag("login", login)
                .register(meterRegistry)
                .increment();
    }
}