import org.gradle.jvm.tasks.Jar

plugins {
    id("java-library")
    id("com.google.protobuf")
}

sourceSets {
    main {
        java {
            srcDir("build/generated/source/proto/main/java")
            srcDir("build/generated/source/proto/main/grpc")
        }
    }
}

dependencies {
    api("io.grpc:grpc-stub:${property("grpcVersion")}")
    api("io.grpc:grpc-protobuf:${property("grpcVersion")}")
    api("com.google.protobuf:protobuf-java:${property("protobufVersion")}")

    runtimeOnly("io.grpc:grpc-netty-shaded:${property("grpcVersion")}")

    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
}

// dependencies {
//     implementation("io.grpc:grpc-netty-shaded:${property("grpcVersion")}")
//     implementation("io.grpc:grpc-protobuf:${property("grpcVersion")}")
//     implementation("io.grpc:grpc-stub:${property("grpcVersion")}")
//     implementation("com.google.protobuf:protobuf-java:${property("protobufVersion")}")

//     // Needed for generated annotations like @Generated
//     compileOnly("org.apache.tomcat:annotations-api:6.0.53")
// }

protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${property("protocVersion")}"
    }
    plugins {
        create("grpc") {
            artifact = "io.grpc:protoc-gen-grpc-java:${property("grpcVersion")}"
        }
    }
    generateProtoTasks {
        all().forEach {
            it.plugins {
                create("grpc")
            }
        }
    }
}

// Tell other projects where to find generated classes
tasks.named<Jar>("jar") {
    archiveBaseName.set("contracts")
}
