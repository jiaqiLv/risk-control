plugins {
    id("java-library")
}

dependencies {
    api("org.springframework.boot:spring-boot-starter:3.2.2")
    api("org.springframework.boot:spring-boot-starter-web:3.2.2")
    api("org.springframework.boot:spring-boot-starter-validation:3.2.2")
    api("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    api("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3")

    // OpenTelemetry
    api("io.opentelemetry:opentelemetry-api:1.36.0")
    api("io.opentelemetry:opentelemetry-extension-trace-propagators:1.36.0")

    // Resilience4j
    api("io.github.resilience4j:resilience4j-core:2.1.0")
    api("io.github.resilience4j:resilience4j-spring:2.1.0")
    api("io.github.resilience4j:resilience4j-retry:2.1.0")
    api("io.github.resilience4j:resilience4j-timelimiter:2.1.0")
    api("io.github.resilience4j:resilience4j-circuitbreaker:2.1.0")

    // ID generation
    api("com.fasterxml.uuid:java-uuid-generator:4.1.0")
}
