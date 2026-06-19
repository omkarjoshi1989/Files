package com.gmail.omkarjoshi1989.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleSidecarResolverTest {

    @Test
    fun picks_exact_base_match_first() {
        val candidates = listOf(
            "movie.en.srt",
            "movie.srt",
            "other.srt"
        )

        val selected = SubtitleSidecarResolver.findBestMatchingSrt(
            videoName = "movie.mkv",
            candidates = candidates,
            nameSelector = { it }
        )

        assertEquals("movie.srt", selected)
    }

    @Test
    fun picks_suffix_match_when_exact_missing() {
        val candidates = listOf(
            "movie.en.srt",
            "movie.hi.srt",
            "other.srt"
        )

        val selected = SubtitleSidecarResolver.findBestMatchingSrt(
            videoName = "movie.mp4",
            candidates = candidates,
            nameSelector = { it }
        )

        assertEquals("movie.en.srt", selected)
    }

    @Test
    fun falls_back_to_single_srt_in_folder() {
        val candidates = listOf(
            "poster.jpg",
            "captions.srt",
            "notes.txt"
        )

        val selected = SubtitleSidecarResolver.findBestMatchingSrt(
            videoName = "random_video.avi",
            candidates = candidates,
            nameSelector = { it }
        )

        assertEquals("captions.srt", selected)
    }

    @Test
    fun returns_null_when_multiple_unrelated_srt_files_exist() {
        val candidates = listOf(
            "english.srt",
            "spanish.srt",
            "poster.jpg"
        )

        val selected = SubtitleSidecarResolver.findBestMatchingSrt(
            videoName = "movie.mkv",
            candidates = candidates,
            nameSelector = { it }
        )

        assertNull(selected)
    }

    @Test
    fun supports_case_insensitive_matching() {
        val candidates = listOf(
            "Movie.EN.SRT",
            "Poster.JPG"
        )

        val selected = SubtitleSidecarResolver.findBestMatchingSrt(
            videoName = "movie.MP4",
            candidates = candidates,
            nameSelector = { it }
        )

        assertEquals("Movie.EN.SRT", selected)
    }
}

