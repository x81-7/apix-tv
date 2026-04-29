rootProject.name = "apix-pc"
include(":app")

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        // JCEF — Chromium Embedded Framework
        maven("https://repo.spring.io/milestone")
        maven("https://jogamp.org/deployment/maven")
    }
}