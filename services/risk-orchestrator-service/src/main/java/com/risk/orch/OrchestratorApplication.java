package com.risk.orch;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Risk Orchestrator Service Application
 *
 * Main entry point for the Risk Orchestrator Service.
 * Orchestrates calls to feature service, decision service, and Python inference service.
 */
@SpringBootApplication
public class OrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrchestratorApplication.class, args);
    }
}
