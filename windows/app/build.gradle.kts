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

    // JCEF — Chromium Embedded Framework
    implementation("me.friwi:jcefmaven:127.3.1")

    // VLCJ
    implementation("uk.co.caprica:vlcj:4.8.3")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // Windows registry access
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
            
            // حل مشكلة ملف الترخيص (تجاهله إن لم يوجد)
            val license = project.rootProject.file("LICENSE.txt")
            if (license.exists()) {
                licenseFile.set(license)
            }

            windows {
                menuGroup = "APiX"
                upgradeUuid = "6951EAA2-1264-4A1A-A86D-817E462202C7"
                shortcut = true
                dirChooser = true
                perUserInstall = true
            }

            modules("java.sql", "jdk.unsupported", "java.naming")
        }

        // استدعاء بصمة التطبيق الممررة من GitHub Secrets
        val appFingerprint = System.getenv("APP_FINGERPRINT")

        // إذا كان السيكرت موجوداً (ليس فارغاً)، سنقوم بتشغيل سكربت التوقيع
        if (!appFingerprint.isNullOrBlank()) {
            afterEvaluate {
                tasks.findByName("packageMsi")?.doLast {
                    val signScript = project.rootProject.file("windows/sign-self.ps1")
                    if (signScript.exists() && System.getProperty("os.name").lowercase().contains("win")) {
                        project.exec {
                            // نمرر الـ appFingerprint كـ Argument لسكربت الباورشيل
                            commandLine("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", signScript.absolutePath, appFingerprint)
                            isIgnoreExitValue = true
                        }
                    }
                }
                // تكرار نفس العملية للـ EXE إذا كان السكربت يوقع الـ EXE أيضاً
                tasks.findByName("packageExe")?.doLast {
                    val signScript = project.rootProject.file("windows/sign-self.ps1")
                    if (signScript.exists() && System.getProperty("os.name").lowercase().contains("win")) {
                        project.exec {
                            commandLine("powershell.exe", "-ExecutionPolicy", "Bypass", "-File", signScript.absolutePath, appFingerprint)
                            isIgnoreExitValue = true
                        }
                    }
                }
            }
        }
    }
}
