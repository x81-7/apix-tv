package com.apix.pc.data

import java.util.Properties

/**
 * White-label runtime config for the Windows desktop client.
 *
 * Values are baked into the packaged app at build time (see the
 * `generateApixConfig` task in build.gradle.kts, which snapshots the CI env
 * into `apix_config.properties` on the classpath). A matching environment
 * variable still overrides the baked value for local testing.
 *
 * Resolution order for each key: ENV → bundled properties → default.
 */
object ApixConfig {

    private val props: Properties by lazy {
        val p = Properties()
        runCatching {
            ApixConfig::class.java.getResourceAsStream("/apix_config.properties")?.use { p.load(it) }
        }
        p
    }

    fun value(key: String, default: String = ""): String {
        System.getenv(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        props.getProperty(key)?.takeIf { it.isNotBlank() }?.let { return it.trim() }
        return default
    }

    /** Worker origin takes priority; CLOUD_URL is the legacy fallback. */
    val baseUrl: String
        get() = (value("WORKER_URL").ifBlank { value("CLOUD_URL") }).trimEnd('/')

    val anonKey: String get() = value("CLOUD_ANON_KEY")

    /** External-link AES-256-GCM decryption key (same as Android BuildConfig.X_DP_K). */
    val externalDecryptKey: String get() = value("X_DP_K")

    /** AES key used by encrypted backend payload envelopes. */
    val payloadEncryptionKey: String get() = value("ENCRYPTION_SECRET_KEY")
}
