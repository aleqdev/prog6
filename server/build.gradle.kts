plugins {
    kotlin("jvm")
    application
}

dependencies {
    implementation(project(":shared"))
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("org.example.ServerMainKt")
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "org.example.ServerMainKt"
    }
    from({
        configurations.runtimeClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    })
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
