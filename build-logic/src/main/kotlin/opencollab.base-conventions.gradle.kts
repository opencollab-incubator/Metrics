plugins {
    `java-library`
    id("net.kyori.indra")
}

val rootProperties: Map<String, *> = project.rootProject.properties
group = rootProperties["group"] as String + "." + rootProperties["id"] as String
version = rootProperties["version"] as String
description = rootProperties["description"] as String

indra {
    github("OpenCollaboration", "MagicMetrics") {
        ci(true)
        issues(true)
        scm(true)
    }
    mitLicense()

    javaVersions {
        target(21)
    }
}

dependencies {
    //compileOnly("org.checkerframework:checker-qual:" + libs.checker.qual.get().version)
}

repositories {
    // mavenLocal()

    mavenCentral()

    // Floodgate, Cumulus etc.
    maven("https://repo.opencollab.dev/main")

    // Jitpack for e.g. MCPL
    maven("https://jitpack.io") {
        content { includeGroupByRegex("com\\.github\\..*") }
    }
}
