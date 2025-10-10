rootProject.name = "[星火核心]1.21.1-neoforge"

pluginManagement {
    repositories {
        mavenLocal()
        gradlePluginPortal()
        maven { setUrl("https://maven.neoforged.net/releases") }
    }
}

plugins {
    // This plugin allows Gradle to automatically download arbitrary versions of Java for you
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

