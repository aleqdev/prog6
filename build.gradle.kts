plugins {
    kotlin("jvm") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("tools.jackson.dataformat:jackson-dataformat-xml:3.1.2")
    implementation("tools.jackson.module:jackson-module-kotlin:3.1.2")
    implementation("ch.qos.logback:logback-classic:1.5.6")
    implementation("org.apache.logging.log4j:log4j-api:2.26.0")
    implementation("org.apache.logging.log4j:log4j-core:2.26.0")
    implementation(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(22)
}
tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.example.MainKt"
    }
    from({
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}