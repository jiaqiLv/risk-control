package com.risk.gateway.config;

import com.alibaba.csp.sentinel.adapter.spring.webflux.callback.WebFluxCallbackManager;
import com.alibaba.csp.sentinel.adapter.spring.webflux.SentinelWebFluxFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

import jakarta.annotation.PostConstruct;

/**
 * Sentinel WebFlux configuration.
 * Registers custom block exception handler.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SentinelWebFluxConfig {

    private final SentinelExceptionHandler sentinelExceptionHandler;

    /**
     * Register Sentinel WebFlux filter.
     * Sentinel 1.8.6 uses no-arg constructor.
     */
    @Bean
    public WebFilter sentinelWebFluxFilter() {
        return new SentinelWebFluxFilter();
    }

    /**
     * Register custom block exception handler.
     */
    @PostConstruct
    public void initBlockHandler() {
        WebFluxCallbackManager.setBlockHandler(sentinelExceptionHandler);
        log.info("Registered custom Sentinel block exception handler");
    }
}
