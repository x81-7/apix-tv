package com.apix.app.vod.engine

import android.util.Log
import com.apix.app.vod.extractors.ApixProvider
import com.apix.app.vod.extractors.StreamSource
import com.apix.app.vod.extractors.SubtitleSource
import com.apix.app.vod.extractors.WatchRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

class SourceEngine(private val providers: List<ApixProvider>) {

    suspend fun fetchStreams(request: WatchRequest): Map<String, List<StreamSource>> = withContext(Dispatchers.IO) {
        val activeProviders = providers.filter { 
            HealthChecker.isHealthy(it.name) && 
            it.supportedTypes.contains(if (request.isSeries) "series" else "movies") 
        }

        val deferredStreams = activeProviders.map { provider ->
            async {
                try {
                    val result = provider.getStreams(request)
                    if (result.isNotEmpty()) {
                        HealthChecker.recordSuccess(provider.name)
                    } else {
                        HealthChecker.recordFailure(provider.name)
                    }
                    result
                } catch (e: Exception) {
                    Log.w("SourceEngine", "Provider ${provider.name} failed streams", e)
                    HealthChecker.recordFailure(provider.name)
                    emptyList()
                }
            }
        }

        val allStreams = deferredStreams.awaitAll().flatten()
        return@withContext PriorityResolver.groupAndSortStreams(allStreams)
    }

    suspend fun fetchSubtitles(request: WatchRequest): List<SubtitleSource> = withContext(Dispatchers.IO) {
        val activeProviders = providers.filter { HealthChecker.isHealthy(it.name) }

        val deferredSubs = activeProviders.map { provider ->
            async {
                try {
                    provider.getSubtitles(request)
                } catch (e: Exception) {
                    Log.w("SourceEngine", "Provider ${provider.name} failed subtitles", e)
                    emptyList()
                }
            }
        }

        val allSubs = deferredSubs.awaitAll().flatten()
        return@withContext PriorityResolver.sortSubtitles(allSubs)
    }
}
