package com.example.cash.controller;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.example.cash.service.CashService;

@RestController
@RequestMapping("/api/cash")
public class CashController {

    final CashService cashService;

    public CashController(CashService cashService) {
        this.cashService = cashService;
    }

    @PutMapping
    public void putCash(Authentication authentication, @RequestParam Long amount) {
        cashService.putCash(authentication, amount);
    }

    @PostMapping
    public void getCash(Authentication authentication, @RequestParam Long amount) {
        cashService.getCash(authentication, amount);
    }
}
