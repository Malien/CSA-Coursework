import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.3.72"
    kotlin("kapt") version "1.3.72"
    kotlin("plugin.serialization") version "1.3.72"
}

val arrowVersion = "0.10.5"
group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    jcenter()
    maven(url = "https://dl.bintray.com/arrow-kt/arrow-kt/")
}

dependencies {
    implementation(kotlin("stdlib-jdk8")) // kotlin standard library
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7") // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0") // kotlinx.serialization
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-protobuf:0.20.0") // protobuf serialization
    implementation("com.github.snksoft:crc:1.0.2") // CRC checks
    implementation("io.arrow-kt:arrow-core:$arrowVersion") // arrow.core
    implementation("io.arrow-kt:arrow-syntax:$arrowVersion")
    kapt("io.arrow-kt:arrow-meta:$arrowVersion")
    implementation("org.xerial:sqlite-jdbc:3.31.1") // SQLite JDBC driver
    implementation("org.slf4j:slf4j-simple:2.0.0-alpha1") // SLF4J Logger (used by Hikari)
    implementation("com.zaxxer:HikariCP:3.4.5") // JDBC connection pool
    implementation(kotlin("script-runtime")) // Ability to run kotlin scripts
    implementation("commons-codec:commons-codec:1.14") // Apache commons codec

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.2.0") // JUnit 5
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.2.0") // JUnit 5 engine
    testRuntime("org.junit.platform:junit-platform-console:1.2.0")  // JUnit 5 platform
}

tasks {
    compileKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    compileTestKotlin {
        kotlinOptions.jvmTarget = "1.8"
    }

    test {
        useJUnitPlatform()
    }
}

val compileKotlin: KotlinCompile by tasks
compileKotlin.kotlinOptions {
    freeCompilerArgs = listOf(
        "-Xopt-in=kotlin.RequiresOptIn",
        "-Xinline-classes",
        "-Xopt-in=kotlin.ExperimentalUnsignedTypes"
    )
}

val compileTestKotlin: KotlinCompile by tasks
compileTestKotlin.kotlinOptions {
    freeCompilerArgs = listOf(
        "-Xopt-in=kotlin.ExperimentalUnsignedTypes",
        "-Xopt-in=ua.edu.ukma.csa.model.TestingOnly"
    )
}
