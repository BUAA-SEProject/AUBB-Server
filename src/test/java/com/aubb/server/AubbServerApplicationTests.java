package com.aubb.server;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = "spring.docker.compose.enabled=false")
class AubbServerApplicationTests {

    @Test
    void contextLoads() {
    }

}
