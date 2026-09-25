plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":application"))
    implementation(project(":profiles:business-software"))
    implementation(project(":examples:order-management"))
}

application {
    applicationName = "arch-knowledge"
    mainClass = "io.github.yamakenji.archknowledge.cli.MainKt"
}
