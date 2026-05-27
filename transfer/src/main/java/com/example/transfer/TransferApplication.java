package com.example.transfer;

import com.example.shared.config.AccountsRestClientConfig;
import com.example.shared.config.NotificationsRestClientConfig;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

@SpringBootApplication
@Import({AccountsRestClientConfig.class, NotificationsRestClientConfig.class})
public class TransferApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransferApplication.class, args);
    }

}
