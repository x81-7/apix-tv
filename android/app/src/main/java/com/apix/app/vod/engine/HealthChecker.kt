package com.apix.app.vod.engine

import java.util.concurrent.ConcurrentHashMap

object HealthChecker {
    private const val MAX_FAILURES = 3
    private val failureCounts = ConcurrentHashMap<String, Int>()

    fun recordFailure(providerName: String) {
        val current = failureCounts[providerName] ?: 0
        failureCounts[providerName] = current + 1
    }

    fun recordSuccess(providerName: String) {
        failureCounts[providerName] = 0
    }

    fun isHealthy(providerName: String): Boolean {
        val fails = failureCounts[providerName] ?: 0
        return fails < MAX_FAILURES
    }

    fun getHealthReport(): Map<String, Int> {
        return failureCounts.toMap()
    }
    
    fun resetHealth(providerName: String) {
        failureCounts[providerName] = 0
    }
}
