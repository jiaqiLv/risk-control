plugins {
    id("java")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":contracts"))
    implementation(project(":common"))

    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-redis-reactive")

    // gRPC server with Spring Boot
    implementation("net.devh:grpc-server-spring-boot-starter:3.1.0.RELEASE")
    // implementation("net.devh:grpc-server-spring-boot-starter:2.16.0")

    // Actuator
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    // Redis connection pool
    implementation("org.apache.commons:commons-pool2:2.12.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
