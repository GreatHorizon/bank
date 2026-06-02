package com.example.front.controller;

import com.example.front.dto.CashAction;
import com.example.shared.dto.TransferMoneyDto;
import com.example.front.service.AccountsService;
import com.example.front.service.CashService;
import com.example.front.service.TransferService;
import com.example.shared.error.ErrorResponse;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.client.HttpClientErrorException;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.util.List;

/**
 * Контроллер main.html.
 * <p>
 * Используемая модель для main.html:
 * model.addAttribute("name", name);
 * model.addAttribute("birthDate", birthDate.format(DateTimeFormatter.ISO_DATE));
 * model.addAttribute("sum", sum);
 * model.addAttribute("accounts", accounts);
 * model.addAttribute("errors", errors);
 * model.addAttribute("info", info);
 * <p>
 * Поля модели:
 * name - Фамилия Имя текущего пользователя, String (обязательное)
 * birthDate - дата рождения текущего пользователя, String в формате 'YYYY-MM-DD' (обязательное)
 * sum - сумма на счету текущего пользователя, Integer (обязательное)
 * accounts - список аккаунтов, которым можно перевести деньги, List<AccountDto> (обязательное)
 * errors - список ошибок после выполнения действий, List<String> (не обязательное)
 * info - строка успешности после выполнения действия, String (не обязательное)
 * <p>
 * С примерами использования можно ознакомиться в тестовом классе заглушке AccountStub
 */
@Controller
public class MainController {
    private final AccountsService accountsService;
    private final CashService cashService;
    private final TransferService transferService;

    public MainController(AccountsService accountsService, CashService cashService, TransferService transferService) {
        this.accountsService = accountsService;
        this.cashService = cashService;
        this.transferService = transferService;
    }

    /**
     * GET /.
     * Редирект на GET /account
     */
    @GetMapping
    public String index() {
        return "redirect:/account";
    }

    /**
     * GET /account.
     * Что нужно сделать:
     * 1. Сходить в сервис accounts через Gateway API для получения данных аккаунта по REST
     * 2. Заполнить модель main.html полученными из ответа данными
     * 3. Текущего пользователя можно получить из контекста Security
     */
    @GetMapping("/account")
    public String getAccount(Authentication authentication, Model model) {
        try {

            setTemplateInfo(authentication, model);
        } catch (HttpClientErrorException.NotFound ex) {
            model.addAttribute("errors", java.util.List.of("Account not found"));
        } catch (Exception ex) {
            model.addAttribute("errors", java.util.List.of("Account not found or Accounts service is unavailable"));
        }

        return "main";
    }

    /**
     * POST /account.
     * Что нужно сделать:
     * 1. Сходить в сервис accounts через Gateway API для изменения данных текущего пользователя по REST
     * 2. Заполнить модель main.html полученными из ответа данными
     * 3. Текущего пользователя можно получить из контекста Security
     * <p>
     * Изменяемые данные:
     * 1. name - Фамилия Имя
     * 2. birthDate - дата рождения в формате YYYY-DD-MM
     */
    @PostMapping("/account")
    public String editAccount(
            Authentication authentication,
            Model model,
            @RequestParam("name") String name,
            @RequestParam("birthdate") LocalDate birthdate
    ) {
        try {
            accountsService.saveAccount(authentication, name, birthdate);

            setTemplateInfo(authentication, model);
        } catch (HttpClientErrorException.NotFound ex) {
            model.addAttribute("errors", java.util.List.of("Account not found"));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errors", List.of(ex.getMessage()));
        }
        catch (Exception ex) {
            model.addAttribute("errors", java.util.List.of("Account not found or Accounts service is unavailable"));
        }

        return "main";
    }

    private void setTemplateInfo(Authentication authentication, Model model) {
        final var account = accountsService.getAccount(authentication);
        final var accountsForTransfer = accountsService.getAccountsForTransfer(authentication);

        model.addAttribute("name", account.firstName() + " " + account.lastName());

        model.addAttribute("birthdate", account.birthDate());

        model.addAttribute("sum", account.balance());

        model.addAttribute("accounts", accountsForTransfer);
    }

    /**
     * POST /cash.
     * Что нужно сделать:
     * 1. Сходить в сервис cash через Gateway API для снятия/пополнения счета текущего аккаунта по REST
     * 2. Заполнить модель main.html полученными из ответа данными
     * 3. Текущего пользователя можно получить из контекста Security
     * <p>
     * Параметры:
     * 1. value - сумма списания
     * 2. action - GET (снять), PUT (пополнить)
     */
    @PostMapping("/cash")
    public String editCash(
            Model model,
            Authentication authentication,
            @RequestParam("value") int value,
            @RequestParam("action") CashAction action
    ) {
        try {
            cashService.updateBalance(authentication, value, action);

            final var message = switch (action) {
                case GET -> String.format("Снято %s рублей", value);
                case PUT -> String.format("Внесено %s рублей", value);
            };

            model.addAttribute("info", message);
        } catch (HttpClientErrorException.BadRequest ex) {
            model.addAttribute("errors", List.of(extractMessage(ex)));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errors", List.of(ex.getMessage()));
        }

        setTemplateInfo(authentication, model);

        return "main";
    }

    /**
     * POST /transfer.
     * Что нужно сделать:
     * 1. Сходить в сервис accounts через Gateway API для перевода со счета текущего аккаунта на счет другого аккаунта по REST
     * 2. Заполнить модель main.html полученными из ответа данными
     * 3. Текущего пользователя можно получить из контекста Security
     * <p>
     * Параметры:
     * 1. value - сумма списания
     * 2. login - логин пользователя получателя
     */
    @PostMapping("/transfer")
    public String transfer(
            Model model,
            Authentication authentication,
            @RequestParam("value") Long value,
            @RequestParam("login") String login
    ) {
        try {
            final var transferMoneyDto = new TransferMoneyDto(login, value);

            transferService.transfer(authentication, transferMoneyDto);

            model.addAttribute("info", "Средства отправлены");
        } catch (HttpClientErrorException.BadRequest ex) {
            model.addAttribute("errors", List.of(extractMessage(ex)));
        } catch (IllegalArgumentException ex) {
            model.addAttribute("errors", List.of(ex.getMessage()));
        }

        setTemplateInfo(authentication, model);

        return "main";


    }

    private String extractMessage(HttpClientErrorException ex) {
        try {
            ObjectMapper mapper = new ObjectMapper();

            ErrorResponse error = mapper.readValue(
                    ex.getResponseBodyAsString(),
                    ErrorResponse.class
            );

            return error.message();
        } catch (Exception ignored) {
            return ex.getResponseBodyAsString();
        }
    }
}
