import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "2.3.21" apply false
}

// Intermediate directories (profiles, examples, applications) are containers, not Kotlin modules.
val containerProjects = setOf(":profiles", ":examples", ":applications")

subprojects {
    if (path in containerProjects) return@subprojects

    apply(plugin = "org.jetbrains.kotlin.jvm")

    repositories {
        mavenCentral()
    }

    extensions.configure<KotlinJvmProjectExtension> {
        jvmToolchain(25)
    }

    dependencies {
        "testImplementation"(kotlin("test"))
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
