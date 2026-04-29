// Root build for the Windows desktop project. Independent from android/.
plugins {
    kotlin("jvm") version "1.9.23" apply false
    id("org.jetbrains.compose") version "1.6.10" apply false
}

allprojects {
    repositories {
        mavenCentral()
        google()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}