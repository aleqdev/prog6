plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "proga-lab6-kt"

include("shared", "server", "client")
