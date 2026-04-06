plugins {
    kotlin("jvm") version "2.1.0"
    kotlin("plugin.spring") version "2.1.0"
    id("org.springframework.boot") version "3.5.9"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.graalvm.buildtools.native") version "0.10.6"
    id("com.google.devtools.ksp") version "2.1.0-1.0.29"
}

group = "top.phj233"
version = "0.0.1-SNAPSHOT"
description = "SmartDataInsightAgent"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    mavenLocal()
    maven { url = uri("https://maven.aliyun.com/repository/public/") }
    maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-public") }
}
val jimmerVersion = "0.10.6"
val springAiVersion = "1.1.2"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    //kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.1")
    implementation("org.springframework.ai:spring-ai-starter-model-deepseek")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    developmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    runtimeOnly("org.postgresql:postgresql")
    runtimeOnly("com.mysql:mysql-connector-j")
    //minio
    implementation("io.minio:minio:9.0.0")
    //jimmer
    ksp("org.babyfish.jimmer:jimmer-ksp:${jimmerVersion}")
    implementation("org.babyfish.jimmer:jimmer-spring-boot-starter:${jimmerVersion}")
    //sa-token
    implementation("cn.dev33:sa-token-spring-boot3-starter:1.44.0")
    implementation("cn.dev33:sa-token-redis-template:1.44.0")
    implementation("org.apache.commons:commons-pool2")
    implementation("cn.dev33:sa-token-jwt:1.44.0")
    implementation ("cn.hutool:hutool-all:5.8.40")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${springAiVersion}")
    }
}

kotlin {
    sourceSets.main {
        kotlin.srcDir("build/generated/ksp/main/kotlin")
    }
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
