dependencies {
    implementation(project(":core"))
    implementation("org.neo4j.driver:neo4j-java-driver:5.26.0")
    testImplementation("org.neo4j.test:neo4j-harness:5.26.0")
    testImplementation(project(":examples:order-management"))
    testImplementation(project(":profiles:business-software"))
}