package com.riskcontrol.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@SpringBootApplication(scanBasePackages = "com.riskcontrol")
@EnableKafka
public class RiskGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskGatewayApplication.class, args);
    }
}
