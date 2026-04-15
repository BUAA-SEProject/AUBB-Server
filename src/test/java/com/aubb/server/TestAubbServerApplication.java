package com.aubb.server;

import org.springframework.boot.SpringApplication;

public class TestAubbServerApplication {

    public static void main(String[] args) {
        SpringApplication.from(AubbServerApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
