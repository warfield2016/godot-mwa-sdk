// Top-level build file for the SolanaMWA Godot Android Plugin
plugins {
    id("com.android.library") version "8.2.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.22" apply false
}

tasks.register("clean", Delete::class) {
    // Gradle 8.8+ removed Project.buildDir. Use layout.buildDirectory for forward compatibility.
    delete(rootProject.layout.buildDirectory)
}
