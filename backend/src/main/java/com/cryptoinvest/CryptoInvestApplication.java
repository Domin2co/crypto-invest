package com.cryptoinvest;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CryptoInvestApplication {

    public static void main(String[] args) {
        SpringApplication.run(CryptoInvestApplication.class, args);
    }
}
