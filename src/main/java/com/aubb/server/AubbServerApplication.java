package com.aubb.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(exclude = {
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class
})
public class AubbServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(AubbServerApplication.class, args);
    }

}
