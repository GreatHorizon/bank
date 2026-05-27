package com.example.front.service;

import com.example.front.client.CashClient;
import com.example.front.dto.CashAction;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class CashService {
    private final CashClient cashClient;

    public CashService(CashClient cashClient) {
        this.cashClient = cashClient;
    }

    public void updateBalance(Authentication authentication, int value, CashAction action) {
        switch (action) {
            case GET -> cashClient.getCash(authentication, value);
            case PUT -> cashClient.putCash(authentication, value);
        }
    }
}
