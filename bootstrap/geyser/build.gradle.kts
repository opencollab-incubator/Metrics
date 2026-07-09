plugins {
    id("opencollab.platform-conventions")
}

dependencies {
    api(project(":core"))
    compileOnly(libs.geyser.api)
    compileOnly(libs.geyser.core)
}