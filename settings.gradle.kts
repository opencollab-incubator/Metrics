rootProject.name = "MagicMetrics"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.opencollab.dev/main/")
    }

    includeBuild("build-logic")
}

include(":core")
include(":geyser")
include(":wdpe")

project(":geyser").projectDir = file("bootstrap/geyser")
project(":wdpe").projectDir = file("bootstrap/wdpe")