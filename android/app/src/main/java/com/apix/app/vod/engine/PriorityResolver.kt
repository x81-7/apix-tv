package com.apix.app.vod.engine

import com.apix.app.vod.extractors.StreamSource
import com.apix.app.vod.extractors.SubtitleSource

object PriorityResolver {

    fun groupAndSortStreams(streams: List<StreamSource>): Map<String, List<StreamSource>> {
        val grouped = streams.groupBy { it.providerName }
        val sortedMap = mutableMapOf<String, List<StreamSource>>()

        for ((provider, list) in grouped) {
            sortedMap[provider] = list.sortedByDescending { it.quality }
        }

        return sortedMap
    }

    fun sortSubtitles(subtitles: List<SubtitleSource>): List<SubtitleSource> {
        return subtitles.sortedWith(Comparator { a, b ->
            val langA = a.language.lowercase()
            val langB = b.language.lowercase()

            val weightA = when {
                langA.contains("ar") -> 100
                langA.contains("en") -> 50
                else -> 10
            }
            
            val weightB = when {
                langB.contains("ar") -> 100
                langB.contains("en") -> 50
                else -> 10
            }

            weightB.compareTo(weightA)
        })
    }
}
