package com.undy.puttrack.domain

sealed class ParsedCommand {
    data class RecordPutt(val distance: Double, val made: Boolean) : ParsedCommand()
    /** A bare "hit"/"miss" with no distance — caller should reuse the previous putt's distance. */
    data class RepeatDistance(val made: Boolean) : ParsedCommand()
    object Stop : ParsedCommand()
    data class Unrecognized(val raw: String) : ParsedCommand()
}

/**
 * Parses free-form text (typed or transcribed from speech) into a command.
 * Speech recognizers usually transcribe spoken numbers as digits already
 * (e.g. "twenty hit" -> "20 hit"), but a word-number fallback is included
 * for recognizers/locales that don't.
 */
object CommandParser {

    private val HIT_WORDS = setOf("hit", "hits", "make", "made", "makes", "in", "good")
    private val MISS_WORDS = setOf("miss", "misses", "missed", "out", "no", "bad")
    private val STOP_WORDS = setOf("stop", "end", "done", "quit", "finish")

    private val ONES = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "for" to 4, "five" to 5,
        "six" to 6, "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10,
        "eleven" to 11, "twelve" to 12, "thirteen" to 13, "fourteen" to 14, "fifteen" to 15,
        "sixteen" to 16, "seventeen" to 17, "eighteen" to 18, "nineteen" to 19
    )
    private val TENS = mapOf(
        "twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50,
        "sixty" to 60, "seventy" to 70, "eighty" to 80, "ninety" to 90
    )

    /**
     * Tries each candidate transcription in order (as returned by a speech recognizer's
     * n-best list) and returns the first one that parses into a real command. Since the
     * recognizer only ever needs to distinguish a number from "make" or "miss", a lower-ranked
     * candidate is often right even when the top guess is noise.
     */
    fun parseBest(candidates: List<String>): ParsedCommand {
        if (candidates.isEmpty()) return ParsedCommand.Unrecognized("")
        for (candidate in candidates) {
            val result = parse(candidate)
            if (result !is ParsedCommand.Unrecognized) return result
        }
        return ParsedCommand.Unrecognized(candidates.first())
    }

    fun parse(rawText: String): ParsedCommand {
        val cleaned = rawText.lowercase()
            .replace("-", " ")
            .replace(Regex("[^a-z0-9. ]"), " ")
            .trim()
        if (cleaned.isEmpty()) return ParsedCommand.Unrecognized(rawText)

        val tokens = cleaned.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (tokens.isEmpty()) return ParsedCommand.Unrecognized(rawText)

        val hasResultWord = tokens.any { it in HIT_WORDS || it in MISS_WORDS }
        if (!hasResultWord && tokens.any { it in STOP_WORDS }) {
            return ParsedCommand.Stop
        }

        val numberResult = extractNumber(tokens)
        if (numberResult == null) {
            if (!hasResultWord) return ParsedCommand.Unrecognized(rawText)
            val made = tokens.any { it in HIT_WORDS }
            return ParsedCommand.RepeatDistance(made)
        }

        val (distance, consumed) = numberResult
        val rest = tokens.drop(consumed)
        val made = when {
            rest.any { it in HIT_WORDS } -> true
            rest.any { it in MISS_WORDS } -> false
            else -> return ParsedCommand.Unrecognized(rawText)
        }
        return ParsedCommand.RecordPutt(distance, made)
    }

    private fun extractNumber(tokens: List<String>): Pair<Double, Int>? {
        val first = tokens.getOrNull(0) ?: return null

        first.toDoubleOrNull()?.let { return it to 1 }

        TENS[first]?.let { tensValue ->
            val second = tokens.getOrNull(1)
            val onesValue = second?.let { ONES[it] }
            return if (onesValue != null && onesValue < 10) {
                (tensValue + onesValue).toDouble() to 2
            } else {
                tensValue.toDouble() to 1
            }
        }

        ONES[first]?.let { return it.toDouble() to 1 }

        return null
    }
}
