package com.riskcontrol.graph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.riskcontrol")
public class GraphServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(GraphServiceApplication.class, args);
    }
}
