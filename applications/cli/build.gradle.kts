plugins {
    application
}

dependencies {
    implementation(project(":core"))
    implementation(project(":application"))
    implementation(project(":profiles:business-software"))
    implementation(project(":examples:order-management"))
    implementation(project(":adapters:neo4j"))
    implementation(project(":adapters:llm-langchain4j"))
    implementation("org.neo4j.driver:neo4j-java-driver:5.26.0")
    testImplementation("org.neo4j.test:neo4j-harness:5.26.0")
}

application {
    applicationName = "arch-knowledge"
    mainClass = "io.github.yamakenji.archknowledge.cli.MainKt"
}
