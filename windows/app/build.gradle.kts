import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)

    // Networking
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")

    // JSON
    implementation("org.json:json:20240303")

    // Image loading for Compose Desktop
    implementation("io.coil-kt.coil3:coil-compose:3.0.4")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.0.4")

    // JCEF — Chromium Embedded Framework (DRM/EME/MSE/ClearKey/Widevine)
    implementation("me.friwi:jcefmaven:127.3.1")

    // VLCJ — fallback player for HLS / MP4 / direct streams with custom headers
    implementation("uk.co.caprica:vlcj:4.8.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // Windows registry access (Anti-Tamper UUID)
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("net.java.dev.jna:jna-platform:5.14.0")

    testImplementation(kotlin("test"))
}

compose.desktop {
    application {
        mainClass = "com.apix.pc.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            packageName = "APiX TV"
            packageVersion = "1.0.0"
            description = "APiX TV — Official Windows Desktop client for streaming live channels."
            copyright = "© 2026 APiX. All rights reserved."
            vendor = "APiX Media"
            licenseFile.set(project.rootProject.file("LICENSE.txt"))

            windows {
                menuGroup = "APiX"
                upgradeUuid = "6951EAA2-1264-4A1A-A86D-817E462202C7"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                // iconFile.set(project.file("src/main/resources/app.ico"))
            }

            modules("java.sql", "jdk.unsupported", "java.naming")
        }

        // Self-signed code-signing applied AFTER native packaging completes.
        // Reduces SmartScreen "Unknown Publisher" prompt severity and helps
        // some Antivirus engines (Avast/AVG/Kaspersky) lower their heuristic
        // score against unsigned PyInstaller-style binaries.
        // Requires PowerShell + signtool (Windows SDK) on the build agent.
        // See windows/SIGNING.md for the one-time cert generation.
        afterEvaluate {
            tasks.findByName("packageMsi")?.doLast {
                val signScript = project.rootProject.file("windows/sign-self.ps1")
                if (signScript.exists() && System.getProperty("os.name").lowercase().contains("win")) {
                    project.exec {
                        commandLine("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", signScript.absolutePath)
                        isIgnoreExitValue = true
                    }
                }
            }
        }
    }
}