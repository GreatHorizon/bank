package com.example.transfer.metrics;

import io.micrometer.core.instrument.Counter;

import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.stereotype.Component;

@Component
public class TransferMetrics {
    private final MeterRegistry meterRegistry;

    public TransferMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void failedTransfer(String fromLogin, String toLogin) {
        Counter.builder("transfer_failed_total")
                .description("Number of failed transfer attempts")
                .tag("from_login", fromLogin)
                .tag("to_login", toLogin)
                .register(meterRegistry)
                .increment();

    }
}