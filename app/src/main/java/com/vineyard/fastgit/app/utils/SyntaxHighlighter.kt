package com.vineyard.fastgit.app.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import java.util.regex.Pattern

object SyntaxHighlighter {

    private val KEYWORD_COLOR = Color(0xFFFF7B72) // Coral red
    private val STRING_COLOR = Color(0xFFA5D6FF)  // Soft light blue
    private val COMMENT_COLOR = Color(0xFF8B949E) // Gray
    private val NUMBER_COLOR = Color(0xFF79C0FF)  // Cyan
    private val ANNOTATION_COLOR = Color(0xFFD2A8FF) // Purple
    private val DEFAULT_TEXT_COLOR = Color(0xFFC9D1D9) // Light off-white
    private val SEARCH_MATCH_BG = Color(0xFFF2CC60).copy(alpha = 0.45f) // Golden highlight for search matches
    private val SEARCH_MATCH_TEXT = Color(0xFFFFFFFF)

    private val KEYWORDS = setOf(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "class", "const",
        "continue", "default", "do", "double", "else", "enum", "extends", "final", "finally",
        "float", "for", "goto", "if", "implements", "import", "instanceof", "int", "interface",
        "long", "native", "new", "package", "private", "protected", "public", "return", "short",
        "static", "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
        "transient", "try", "void", "volatile", "while", "fun", "val", "var", "when", "sealed",
        "data", "object", "typealias", "override", "open", "internal", "companion", "lateinit",
        "by", "in", "is", "where", "suspend", "coroutine", "flow", "state", "recompose", "true",
        "false", "null", "val", "var", "implementation", "api", "testImplementation",
        "androidTestImplementation", "kapt", "ksp", "plugins", "id", "version", "apply", "from"
    )

    // Precompiled high-speed parsing pattern executing sequentially across the document stream
    private val COMBINED_PATTERN = Pattern.compile(
        "(//[^\\r\\n]*|/\\*[\\s\\S]*?\\*/|#[^\\r\\n]*)" +                      // Group 1: Comments
        "|(\"[^\"\\\\]*(?:\\\\.[^\"\\\\]*)*\"|'[^'\\\\]*(?:\\\\.[^'\\\\]*)*')" +  // Group 2: Strings
        "|\\b(" + KEYWORDS.joinToString("|") + ")\\b" +                         // Group 3: Keywords
        "|(@\\w+)" +                                                            // Group 4: Annotations
        "|(\\b\\d+\\b)"                                                         // Group 5: Numbers
    )

    /**
     * Highlights the provided code with language syntax coloring and optional search query match styling.
     */
    fun highlight(
        code: String,
        fileName: String = "",
        searchQuery: String = "",
        isCaseSensitive: Boolean = false,
        isRegex: Boolean = false
    ): AnnotatedString {
        if (code.isEmpty()) {
            return AnnotatedString("")
        }

        val baseBuilder = AnnotatedString.Builder()
        val matcher = COMBINED_PATTERN.matcher(code)
        var lastIndex = 0

        while (matcher.find()) {
            val start = matcher.start()
            val end = matcher.end()

            // Append unstyled text segments prior to matching index
            if (start > lastIndex) {
                baseBuilder.append(code.substring(lastIndex, start))
            }

            // Style matching segments based on capture group indices
            when {
                matcher.group(1) != null -> { // Comments
                    baseBuilder.withStyle(SpanStyle(color = COMMENT_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                matcher.group(2) != null -> { // Strings
                    baseBuilder.withStyle(SpanStyle(color = STRING_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                matcher.group(3) != null -> { // Keywords
                    baseBuilder.withStyle(SpanStyle(color = KEYWORD_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                matcher.group(4) != null -> { // Annotations
                    baseBuilder.withStyle(SpanStyle(color = ANNOTATION_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                matcher.group(5) != null -> { // Numbers
                    baseBuilder.withStyle(SpanStyle(color = NUMBER_COLOR, fontFamily = FontFamily.Monospace)) {
                        append(code.substring(start, end))
                    }
                }
                else -> {
                    baseBuilder.append(code.substring(start, end))
                }
            }
            lastIndex = end
        }

        // Append remaining unstyled text segments
        if (lastIndex < code.length) {
            baseBuilder.append(code.substring(lastIndex))
        }

        val baseAnnotated = baseBuilder.toAnnotatedString()

        // If no active search query exists, return the syntax-highlighted string directly
        if (searchQuery.isEmpty()) {
            return baseAnnotated
        }

        // Overlay active search match highlights across the styled text
        val finalBuilder = AnnotatedString.Builder(baseAnnotated)
        try {
            val searchMatcher = if (isRegex) {
                val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
                Pattern.compile(searchQuery, flags).matcher(code)
            } else {
                val flags = if (isCaseSensitive) 0 else Pattern.CASE_INSENSITIVE
                Pattern.compile(Pattern.quote(searchQuery), flags).matcher(code)
            }

            while (searchMatcher.find()) {
                val matchStart = searchMatcher.start()
                val matchEnd = searchMatcher.end()
                if (matchStart < matchEnd) {
                    finalBuilder.addStyle(
                        style = SpanStyle(
                            background = SEARCH_MATCH_BG,
                            color = SEARCH_MATCH_TEXT,
                            fontWeight = FontWeight.Bold
                        ),
                        start = matchStart,
                        end = matchEnd
                    )
                }
            }
        } catch (_: Exception) {
            // Ignore incomplete or invalid regex patterns during live typing
        }

        return finalBuilder.toAnnotatedString()
    }
}