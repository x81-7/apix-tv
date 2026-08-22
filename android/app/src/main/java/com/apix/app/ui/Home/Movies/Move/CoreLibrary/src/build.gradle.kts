import com.codingfeline.buildkonfig.compiler.FieldSpec
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.multiplatform.library)
    alias(libs.plugins.buildkonfig)
}

kotlin {
    android {
        namespace = "com.lagradost.api"
        compileSdk = 36
        minSdk = 23
        compilerOptions { jvmTarget.set(JvmTarget.JVM_1_8) }
    }
    jvm()
    compilerOptions {
        freeCompilerArgs.addAll(
            "-Xexpect-actual-classes",
            "-Xannotation-default-target=param-property"
        )
    }
    sourceSets {
        all {
            languageSettings {
                optIn("com.lagradost.cloudstream3.InternalAPI")
                optIn("com.lagradost.cloudstream3.Prerelease")
            }
        }
        commonMain.dependencies {
            implementation(libs.annotation)
            implementation(libs.jackson.module.kotlin)
            implementation(libs.jsoup)
            implementation(libs.kotlinx.atomicfu)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ksoup)
            implementation(libs.ktor.http)
            implementation(libs.nicehttp)
            implementation(libs.rhino)
            implementation(libs.tmdb.java)
            implementation(libs.bundles.cryptography)
            implementation("me.xdrop:fuzzywuzzy:1.4.0")
        }
        val jvmCommonMain by creating {
            dependsOn(commonMain.get())
            dependencies { implementation(libs.newpipeextractor) }
        }
        androidMain { dependsOn(jvmCommonMain) }
        jvmMain { dependsOn(jvmCommonMain) }
    }
}

buildkonfig {
    packageName = "com.lagradost.api"
    exposeObjectWithName = "BuildConfig"
    defaultConfigs {
        buildConfigField(FieldSpec.Type.STRING, "MDL_API_KEY", System.getenv("MDL_KEY") ?: "")
        buildConfigField(FieldSpec.Type.STRING, "TRAKT_CLIENT_ID", System.getenv("TRAKT_CLIENT_ID") ?: "")
    }
}
