plugins {
    id("opencollab.platform-conventions")
}

dependencies {
    api(project(":core"))
    compileOnly(libs.wdpe)
}