val spaceSdk: String = project.findProperty("space.sdk") as String
val clikt: String by project
val ktor_version: String by project
val kotlin_serialization_version: String by project

plugins {
    kotlin("jvm") version "2.0.20"
    application
    kotlin("plugin.serialization") version "2.0.20"
}

group = "org.jetbrains"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/space/maven")
}

dependencies {
    implementation("org.jetbrains:space-sdk-jvm:$spaceSdk")
    implementation("com.github.ajalt.clikt:clikt:$clikt")
    implementation("io.ktor:ktor-client-java:$ktor_version")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlin_serialization_version")

    implementation("io.github.microutils:kotlin-logging-jvm:3.0.5")
    implementation("org.slf4j:slf4j-api:2.0.1")
    implementation("org.slf4j:slf4j-simple:2.0.1")
    implementation("ch.qos.logback:logback-classic:1.4.12")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

application {
    mainClass = "org.jetbrains.MainKt"
}

