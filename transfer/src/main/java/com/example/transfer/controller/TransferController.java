package com.example.transfer.controller;

import com.example.shared.dto.TransferMoneyDto;
import com.example.transfer.service.TransferService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transfer")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    void transferMoney(
            Authentication authentication,
            @RequestBody TransferMoneyDto transferMoneyDto
    ) {
        transferService.transferMoney(authentication, transferMoneyDto);
    }
}
