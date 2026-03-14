plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jlleitschuh.gradle.ktlint") version "12.1.2"
}

group = "io.vedra"
version = "1.0-SNAPSHOT"
description = "Vedra - Your AI-Powered Knowledgebase Assistant"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
}

kotlin {
    jvmToolchain(24)
}

tasks.test {
    useJUnitPlatform()
}

ktlint {
    version.set("1.5.0")
}

tasks.check {
    dependsOn("ktlintCheck")
}
