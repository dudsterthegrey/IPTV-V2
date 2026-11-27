package com.streamvault.data

import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

/**
 * Desktop Java accepts a bare `}` in a regex, but Android's ICU engine throws
 * PatternSyntaxException, which crashed the subtitle search on devices while every JVM test
 * passed. This scans the module's raw-string Regex literals for that mistake.
 */
class AndroidRegexSyntaxTest {
    private val literal = Regex("Regex\\(\\s*\"\"\"(.*?)\"\"\"", RegexOption.DOT_MATCHES_ALL)

    @Test
    fun `regex literals escape closing brackets and braces`() {
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.toList()
        assertWithMessage("no sources found from ${File(".").absolutePath}").that(sources).isNotEmpty()
        val problems = sources.flatMap { file ->
            literal.findAll(file.readText()).mapNotNull { match ->
                val pattern = match.groupValues[1]
                bareClosingIndex(pattern)?.let { "${file.name}: $pattern (index $it)" }
            }.toList()
        }
        assertWithMessage("Unescaped } would crash on Android").that(problems).isEmpty()
    }

    /** Index of a `}` that closes no quantifier, or null when the pattern is fine for ICU. */
    private fun bareClosingIndex(pattern: String): Int? {
        var inClass = false
        var classStart = -1
        var inQuantifier = false
        var i = 0
        while (i < pattern.length) {
            val c = pattern[i]
            when {
                c == '\\' -> i++
                c == '$' && i + 1 < pattern.length && pattern[i + 1] == '{' -> {
                    // A Kotlin string template, not regex syntax.
                    i = pattern.indexOf('}', i).takeIf { it >= 0 } ?: pattern.length
                }
                inClass -> if (c == ']' && i > classStart + 1 && !(i == classStart + 2 && pattern[classStart + 1] == '^')) inClass = false
                c == '[' -> { inClass = true; classStart = i }
                c == '{' && i + 1 < pattern.length && pattern[i + 1].isDigit() -> inQuantifier = true
                c == '}' && inQuantifier -> inQuantifier = false
                c == '}' -> return i
            }
            i++
        }
        return null
    }
}
