package com.gmail.omkarjoshi1989.util

object SubtitleSidecarResolver {

    private val preferredSubtitleTokens = listOf(
        "english", "eng", "en", "default", "sub", "subs", "caption", "captions"
    )

    fun <T> findBestMatchingSrt(
        videoName: String,
        candidates: List<T>,
        nameSelector: (T) -> String
    ): T? {
        val videoBase = normalizedBaseName(videoName)
        if (videoBase.isBlank()) return null

        val srtCandidates = candidates.filter { nameSelector(it).endsWith(".srt", ignoreCase = true) }
        if (srtCandidates.isEmpty()) return null

        val exactMatch = srtCandidates.firstOrNull {
            normalizedBaseName(nameSelector(it)) == videoBase
        }
        if (exactMatch != null) return exactMatch

        val boundaryMatch = srtCandidates
            .map { it to normalizedBaseName(nameSelector(it)) }
            .filter { (_, subtitleBase) ->
                startsWithBoundary(subtitleBase, videoBase) || startsWithBoundary(videoBase, subtitleBase)
            }
            .minByOrNull { (_, subtitleBase) -> kotlin.math.abs(subtitleBase.length - videoBase.length) }
            ?.first
        if (boundaryMatch != null) return boundaryMatch

        val videoTokens = tokens(videoBase)
        val tokenPrefixMatch = srtCandidates
            .map { it to normalizedBaseName(nameSelector(it)) }
            .mapNotNull { (candidate, subtitleBase) ->
                val subtitleTokens = tokens(subtitleBase)
                val commonPrefix = commonLeadingTokenCount(videoTokens, subtitleTokens)
                if (commonPrefix >= 2) {
                    Triple(candidate, commonPrefix, kotlin.math.abs(subtitleTokens.size - videoTokens.size))
                } else {
                    null
                }
            }
            .maxWithOrNull(
                compareBy<Triple<T, Int, Int>> { it.second }
                    .thenByDescending { -it.third }
            )
            ?.first
        if (tokenPrefixMatch != null) return tokenPrefixMatch

        val looseVideoKey = looseKey(videoBase)
        if (looseVideoKey.isNotBlank()) {
            val looseMatch = srtCandidates
                .map { candidate -> candidate to looseKey(normalizedBaseName(nameSelector(candidate))) }
                .mapNotNull { (candidate, subtitleKey) ->
                    if (subtitleKey.isBlank()) return@mapNotNull null
                    val longer = maxOf(looseVideoKey.length, subtitleKey.length)
                    val shorter = minOf(looseVideoKey.length, subtitleKey.length)
                    val ratio = if (longer == 0) 0f else shorter.toFloat() / longer.toFloat()
                    if ((looseVideoKey.startsWith(subtitleKey) || subtitleKey.startsWith(looseVideoKey)) && ratio >= 0.6f) {
                        candidate to kotlin.math.abs(looseVideoKey.length - subtitleKey.length)
                    } else {
                        null
                    }
                }
                .minByOrNull { (_, lengthDelta) -> lengthDelta }
                ?.first
            if (looseMatch != null) return looseMatch
        }

        // Final fallback: still pick an available subtitle deterministically.
        // This guarantees playback can attach at least one sidecar when a folder
        // has multiple unrelated .srt files.
        return srtCandidates
            .sortedWith(
                compareByDescending<T> { subtitlePreferenceScore(nameSelector(it)) }
                    .thenBy { normalizedBaseName(nameSelector(it)).length }
                    .thenBy { nameSelector(it).lowercase() }
            )
            .firstOrNull()
    }

    private fun subtitlePreferenceScore(fileName: String): Int {
        val name = normalizedBaseName(fileName)
        var score = 0
        preferredSubtitleTokens.forEachIndexed { index, token ->
            if (name.contains(token)) {
                // Earlier tokens are stronger preferences.
                score += (preferredSubtitleTokens.size - index)
            }
        }
        return score
    }

    private fun normalizedBaseName(fileName: String): String {
        return fileName
            .substringBeforeLast('.', fileName)
            .trim()
            .lowercase()
    }

    private fun startsWithBoundary(value: String, prefix: String): Boolean {
        if (value == prefix) return true
        if (!value.startsWith(prefix) || value.length <= prefix.length) return false
        return when (value[prefix.length]) {
            '.', ' ', '_', '-' -> true
            else -> false
        }
    }

    private fun looseKey(value: String): String =
        value.lowercase().filter { it.isLetterOrDigit() }

    private fun tokens(value: String): List<String> =
        value.lowercase().split(Regex("[^a-z0-9]+"))
            .filter { it.isNotBlank() }

    private fun commonLeadingTokenCount(left: List<String>, right: List<String>): Int {
        val limit = minOf(left.size, right.size)
        var count = 0
        while (count < limit && left[count] == right[count]) {
            count++
        }
        return count
    }
}

