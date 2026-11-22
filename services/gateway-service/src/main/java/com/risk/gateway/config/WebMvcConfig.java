package com.risk.gateway.config;

import com.risk.gateway.interceptor.DataCollectionInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for registering interceptors.
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final DataCollectionInterceptor dataCollectionInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(dataCollectionInterceptor)
            .addPathPatterns("/api/v1/**")
            .excludePathPatterns("/actuator/**", "/error");
    }
}
