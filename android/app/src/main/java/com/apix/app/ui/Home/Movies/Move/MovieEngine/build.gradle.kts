plugins {
    alias(libs.plugins.android.library)
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.lagradost.cloudstream3"

    sourceSets["main"].java.srcDirs(
        "../Player",
        "../Plugs",
        "../ui",
        "../Core/Shared",
        "../Core/CoreLibraryCompat/src/main/java"
    )
    compileSdk = 36
    defaultConfig {
        minSdk = 23
        consumerProguardFiles("consumer-rules.pro")
        buildConfigField("long", "BUILD_DATE", "${System.currentTimeMillis()}L")
        buildConfigField("String", "SIMKL_CLIENT_ID", "\"${System.getenv("SIMKL_CLIENT_ID") ?: ""}\"")
        buildConfigField("String", "SIMKL_CLIENT_SECRET", "\"${System.getenv("SIMKL_CLIENT_SECRET") ?: ""}\"")
        buildConfigField("String", "MAL_KEY", "\"${System.getenv("MAL_KEY") ?: ""}\"")
        buildConfigField("String", "ANILIST_KEY", "\"${System.getenv("ANILIST_KEY") ?: ""}\"")
        buildConfigField("String", "APIX_WORKER_URL", "\"${System.getenv("APIX_WORKER_URL") ?: ""}\"")
        buildConfigField("String", "APIX_ENCRYPTION_KEY", "\"${System.getenv("APIX_ENCRYPTION_KEY") ?: ""}\"")
        buildConfigField("String", "APIX_HMAC_SECRET", "\"${System.getenv("APIX_HMAC_SECRET") ?: ""}\"")
        buildConfigField("String", "APIX_KEY_SALT", "\"${System.getenv("APIX_KEY_SALT") ?: ""}\"")
        buildConfigField("String", "APIX_TMDB_TOKEN", "\"${System.getenv("APIX_TMDB_TOKEN") ?: ""}\"")
        buildConfigField("String", "APPLICATION_ID", "\"com.apix.app\"")
        buildConfigField("String", "VERSION_NAME", "\"4.7.0\"")
        buildConfigField("String", "FLAVOR", "\"stable\"")
        buildConfigField("int", "VERSION_CODE", "68")
    }
    buildFeatures { buildConfig = true; viewBinding = true; compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    implementation(project(":movies-library"))
    implementation(libs.core.ktx)
    implementation(libs.activity.ktx)
    implementation(libs.annotation)
    implementation(libs.appcompat)
    implementation(libs.fragment.ktx)
    implementation(libs.bundles.lifecycle)
    implementation(libs.bundles.navigation)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.preference.ktx)
    implementation(libs.material)
    implementation(libs.constraintlayout)
    implementation(libs.bundles.coil)
    implementation(libs.bundles.media3)
    implementation(libs.video)
    implementation(libs.bundles.nextlib)
    implementation(libs.anime.db)
    implementation(libs.colorpicker)
    implementation(libs.newpipeextractor)
    implementation(libs.juniversalchardet)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.ui:ui:1.9.3")
    implementation("androidx.compose.ui:ui-tooling-preview:1.9.3")
    implementation("androidx.compose.foundation:foundation:1.9.3")
    implementation("androidx.compose.material3:material3:1.4.0")
    implementation("androidx.compose.material:material-icons-extended:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation(libs.shimmer)
    implementation(libs.palette.ktx)
    implementation(libs.tvprovider)
    implementation(libs.overlappingpanels)
    implementation(libs.biometric)
    implementation(libs.previewseekbar.media3)
    implementation(libs.qrcode.kotlin)
    implementation(libs.jsoup)
    implementation(libs.ksoup)
    implementation(libs.rhino)
    implementation(libs.safefile)
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)
    implementation(libs.conscrypt.android)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.zipline)
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
    implementation(libs.torrentserver)
    implementation(libs.work.runtime.ktx)
    implementation(libs.nicehttp)
}
