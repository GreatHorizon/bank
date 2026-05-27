package com.example.front.service;

import com.example.front.client.TransferClient;
import com.example.shared.dto.TransferMoneyDto;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

@Service
public class TransferService {
    private final TransferClient transferClient;

    public TransferService(TransferClient transferClient) {
        this.transferClient = transferClient;
    }

    public void transfer(Authentication authentication, TransferMoneyDto transferMoneyDto) {
        transferClient.transfer(authentication, transferMoneyDto);
    }
}
