plugins {
    id("opencollab.base-conventions")
    id("io.freefair.lombok")
}

dependencies {
    annotationProcessor(libs.configurate.`interface`.ap)
    api(libs.configurate.yaml)
    api(libs.configurate.core)
    compileOnlyApi(libs.raknet)

    api(platform(libs.micrometer.bom))
    api(libs.micrometer.core)
}