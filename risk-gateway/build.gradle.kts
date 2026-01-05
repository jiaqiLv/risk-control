plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":contracts"))
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Database
    implementation("org.postgresql:r2dbc-postgresql")
    // implementation("io.r2dbc:r2dbc-postgresql")

    implementation("org.flywaydb:flyway-core:10.4.1")
    implementation("org.flywaydb:flyway-database-postgresql:10.4.1")

    // gRPC clients
    implementation("net.devh:grpc-client-spring-boot-starter:3.0.0.RELEASE")

    // Kafka
    implementation("org.springframework.kafka:spring-kafka:3.1.1")

    // Resilience4j
    implementation("io.github.resilience4j:resilience4j-reactor:2.1.0")

    // Actuator and metrics
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.1")

    // OpenTelemetry
    implementation("io.opentelemetry:opentelemetry-api:1.36.0")

    // Jackson
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.3")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.15.3")

    // R2DBC mapping
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor:3.2.2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
}
