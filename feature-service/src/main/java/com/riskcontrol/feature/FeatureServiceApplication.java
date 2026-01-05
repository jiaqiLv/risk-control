package com.riskcontrol.feature;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.riskcontrol")
public class FeatureServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FeatureServiceApplication.class, args);
    }
}
