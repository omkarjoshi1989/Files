package com.gmail.omkarjoshi1989.util

object SubtitleSidecarResolver {

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

        val suffixMatch = srtCandidates
            .filter {
                val subtitleBase = normalizedBaseName(nameSelector(it))
                subtitleBase.startsWith("$videoBase.") ||
                    subtitleBase.startsWith("$videoBase ") ||
                    subtitleBase.startsWith("${videoBase}_") ||
                    subtitleBase.startsWith("${videoBase}-")
            }
            .minByOrNull { nameSelector(it).length }
        if (suffixMatch != null) return suffixMatch

        return if (srtCandidates.size == 1) srtCandidates.first() else null
    }

    private fun normalizedBaseName(fileName: String): String {
        return fileName
            .substringBeforeLast('.', fileName)
            .trim()
            .lowercase()
    }
}

