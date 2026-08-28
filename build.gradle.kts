plugins {
    kotlin("jvm") version "2.0.21"
    application
}

group = "org.alex"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass.set("ledger.MainKt")
}

dependencies {
    testImplementation("org.jetbrains.kotlin:kotlin-test")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

val knownFailureTag = "known-failure"

tasks.test {
    useJUnitPlatform { excludeTags(knownFailureTag) }
    testLogging { events("passed", "skipped", "failed") }
}

tasks.register<Test>("knownFailureTest") {
    group = "verification"
    description = "Runs the one intentionally failing test. Expected to FAIL."
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform { includeTags(knownFailureTag) }
    testLogging { events("passed", "skipped", "failed") }
}
