plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    api("tools.jackson.dataformat:jackson-dataformat-xml:3.1.2")
    api("tools.jackson.module:jackson-module-kotlin:3.1.2")
    api("ch.qos.logback:logback-classic:1.5.6")
    api("org.apache.logging.log4j:log4j-api:2.26.0")
    api("org.apache.logging.log4j:log4j-core:2.26.0")
    api(kotlin("reflect"))
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(17)
}
