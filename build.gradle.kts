plugins {
    kotlin("jvm") version "1.3.72"
    kotlin("kapt") version "1.3.72"
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
    implementation(kotlin("stdlib-jdk8"))
    implementation("com.github.snksoft:crc:1.0.2")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.2.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.2.0")
    testRuntime("org.junit.platform:junit-platform-console:1.2.0")
    implementation("io.arrow-kt:arrow-core:$arrowVersion")
    implementation("io.arrow-kt:arrow-syntax:$arrowVersion")
    kapt("io.arrow-kt:arrow-meta:$arrowVersion")
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