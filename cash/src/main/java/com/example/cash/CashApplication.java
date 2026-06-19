package com.example.cash;

import com.example.shared.config.AccountsRestClientConfig;
import com.example.shared.config.NotificationClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({
        AccountsRestClientConfig.class,
        NotificationClientConfig.class
})
public class CashApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashApplication.class, args);
    }

}
