package com.darkhorses.PedalConnect.ui.theme

/**
 * ProfanityFilter — production-grade, zero external dependencies
 *
 * Pipeline:
 *  1. Normalize a shadow copy of the input (leet → letters, collapse repeats, strip separators)
 *  2. Match each precompiled pattern against the normalized shadow
 *  3. When a match is found, locate and mask the corresponding span in the ORIGINAL text
 *  4. Return (wasCensored, censoredOriginal)
 *
 * Patterns are compiled ONCE at object initialization — never rebuilt per call.
 */
object ProfanityFilter {

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Word list — English + Filipino/Tagalog
    //    Phrases are supported via multi-token entries (spaces allowed in list)
    // ─────────────────────────────────────────────────────────────────────────

    data class ProfanityEntry(
        val word: String,
        val severity: Severity
    )

    enum class Severity { MILD, STRONG }

    private val profanityEntries = listOf(
        // ── English (STRONG) ─────────────────────────────────────────────────
        ProfanityEntry("fuck",      Severity.STRONG),
        ProfanityEntry("fucking",   Severity.STRONG),
        ProfanityEntry("fucker",    Severity.STRONG),
        ProfanityEntry("motherfucker", Severity.STRONG),
        ProfanityEntry("shit",      Severity.STRONG),
        ProfanityEntry("bullshit",  Severity.STRONG),
        ProfanityEntry("bitch",     Severity.STRONG),
        ProfanityEntry("asshole",   Severity.STRONG),
        ProfanityEntry("ass",       Severity.STRONG),   // see StandaloneEntry note below
        ProfanityEntry("bastard",   Severity.STRONG),
        ProfanityEntry("cunt",      Severity.STRONG),
        ProfanityEntry("cock",      Severity.STRONG),
        ProfanityEntry("dick",      Severity.STRONG),
        ProfanityEntry("pussy",     Severity.STRONG),
        ProfanityEntry("whore",     Severity.STRONG),
        ProfanityEntry("slut",      Severity.STRONG),
        ProfanityEntry("faggot",    Severity.STRONG),
        ProfanityEntry("retard",    Severity.STRONG),
        ProfanityEntry("idiot",     Severity.STRONG),
        ProfanityEntry("moron",     Severity.STRONG),
        ProfanityEntry("imbecile",  Severity.STRONG),
        ProfanityEntry("loser",     Severity.STRONG),
        // ── English (MILD) ───────────────────────────────────────────────────
        ProfanityEntry("stupid",    Severity.MILD),
        ProfanityEntry("dumb",      Severity.MILD),
        ProfanityEntry("crap",      Severity.MILD),
        ProfanityEntry("piss",      Severity.MILD),
        ProfanityEntry("damn",      Severity.MILD),
        ProfanityEntry("hell",      Severity.MILD),
        ProfanityEntry("jerk",      Severity.MILD),
        ProfanityEntry("clueless",  Severity.MILD),
        // ── Filipino/Tagalog (STRONG) ────────────────────────────────────────
        ProfanityEntry("putangina",   Severity.STRONG),
        ProfanityEntry("putang ina",  Severity.STRONG), // phrase support
        ProfanityEntry("putang ina mo", Severity.STRONG),
        ProfanityEntry("puta",        Severity.STRONG),
        ProfanityEntry("tangina",     Severity.STRONG),
        ProfanityEntry("hindot",      Severity.STRONG),
        ProfanityEntry("kantot",      Severity.STRONG),
        ProfanityEntry("jakol",       Severity.STRONG),
        ProfanityEntry("pakshet",     Severity.STRONG),
        ProfanityEntry("tarantado",   Severity.STRONG),
        ProfanityEntry("siraulo",     Severity.STRONG),
        ProfanityEntry("gago",        Severity.STRONG),
        ProfanityEntry("gaga",        Severity.STRONG),
        ProfanityEntry("ulol",        Severity.STRONG),
        // ── Filipino/Tagalog (MILD) ──────────────────────────────────────────
        ProfanityEntry("punyeta",  Severity.MILD),
        ProfanityEntry("leche",    Severity.MILD),
        ProfanityEntry("bwisit",   Severity.MILD),
        ProfanityEntry("inutil",   Severity.MILD),
        ProfanityEntry("tanga",    Severity.MILD),
        ProfanityEntry("bobo",     Severity.MILD),
        ProfanityEntry("peste",    Severity.MILD),
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 1b. Protected words — these will NEVER be censored even if they contain
    //     a profanity substring. Checked before any masking is applied.
    //     Add more as false positives are discovered from real usage.
    // ─────────────────────────────────────────────────────────────────────────

    private val protectedWords: Set<String> = setOf(
        // Words containing "ass"
        "class", "classes", "classic", "classical",
        "grass", "grassy",
        "mass", "massive", "massage",
        "pass", "passage", "passenger", "passion", "passive",
        "bass", "bassoon",
        "assemble", "assembly", "assert", "assess", "asset",
        "assign", "assist", "associate", "assume", "assure",
        "assassin", "assault",
        "harass", "embarrass", "ambassador",
        // Words containing "dick"
        "dictionary", "dictate", "diction", "benedict",
        // Words containing "cock"
        "cocktail", "cockatoo", "peacock", "hancock",
        // Words containing "hell"
        "hello", "shell", "shelter", "helmet", "hell",
        // Words containing "damn"
        "condemn",
        // Words containing "piss"
        "mississippi",
        // Words containing "tanga" (Filipino)
        "batangas", "batangeno",
        // Words containing "bobo"
        "bobolink",
        // Words containing "leche" (Filipino — milk)
        "leche flan"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 2. Leet-speak character map
    //    Used both in normalization AND in pattern building
    // ─────────────────────────────────────────────────────────────────────────

    private val leetMap: Map<Char, String> = mapOf(
        'a' to "[a4@]",
        'e' to "[e3]",
        'i' to "[i1!|]",
        'o' to "[o0]",
        's' to "[s5\$]",
        't' to "[t7+]",
        'b' to "[b8]",
        'g' to "[g9]",
        'l' to "[l1|]",
        'z' to "[z2]"
    )

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Pattern cache — compiled ONCE, never rebuilt
    //    Each entry maps to a precompiled Pattern
    // ─────────────────────────────────────────────────────────────────────────

    private data class CompiledEntry(
        val entry: ProfanityEntry,
        val pattern: java.util.regex.Pattern
    )

    private val compiledEntries: List<CompiledEntry> = profanityEntries
        // Sort longest first so "putang ina mo" matches before "puta"
        .sortedByDescending { it.word.length }
        .map { entry -> CompiledEntry(entry, buildPattern(entry.word)) }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Pattern builder
    //    Builds a regex that tolerates:
    //      - leet substitutions per character
    //      - optional separator chars between each letter
    //      - repeated letters (via {1,} quantifier on each char class)
    //      - custom word boundaries (handles punctuation correctly)
    //        Uses lookahead/lookbehind instead of \b to avoid false positives
    //        e.g. "tanga" inside "Batangas" will NOT match
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildPattern(word: String): java.util.regex.Pattern {
        // Optional separator between every character
        // Allows: f.u.c.k  f u c k  f*u*c*k  f-u-c-k
        val sep = "[\\s.\\-_*|]*"

        val patternStr = buildString {
            // Negative lookbehind — must NOT be preceded by a letter/digit
            append("(?<![\\p{L}\\p{N}])")

            word.forEachIndexed { index, c ->
                if (c == ' ') {
                    // Phrase support: space in word list = flexible whitespace in pattern
                    append("[\\s.\\-_*|]+")
                } else {
                    val charClass = leetMap[c.lowercaseChar()] ?: java.util.regex.Pattern.quote(c.toString())
                    // {1,4} allows up to 4 repeated chars e.g. "fuuuck" but stops
                    // runaway matches on very long strings
                    append("$charClass{1,4}")
                    // Add separator after every character except the last
                    if (index < word.length - 1 && word[index + 1] != ' ') {
                        append(sep)
                    }
                }
            }

            // Negative lookahead — must NOT be followed by a letter/digit
            append("(?![\\p{L}\\p{N}])")
        }

        return java.util.regex.Pattern.compile(
            patternStr,
            java.util.regex.Pattern.CASE_INSENSITIVE or java.util.regex.Pattern.UNICODE_CASE
        )
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. Normalizer — produces a SHADOW copy only
    //    Original is never modified here — used only for detection
    //    Safe normalization: does not collapse valid short words incorrectly
    // ─────────────────────────────────────────────────────────────────────────

    fun normalize(text: String): String {
        var result = text.lowercase()

        // Step 1 — strip separator-only clusters between letters
        // e.g. "p.u.t.a" → "puta", "f u c k" → "fuck"
        // Only strips separators that appear between two word characters
        result = result.replace(Regex("(?<=[a-z0-9])[\\s.\\-_*|]+(?=[a-z0-9])"), "")

        // Step 2 — collapse runs of 3+ identical letters → 2
        // Keeps "boob" intact (2 repeats) but collapses "fuuuuck" → "fuuck" → caught
        // Using 2 instead of 1 avoids false positives on words like "boo", "too"
        result = result.replace(Regex("(.)\\1{2,}"), "$1$1")

        // Step 3 — leet speak: simple character substitution on the shadow
        result = result
            .replace('0', 'o')
            .replace('1', 'i')
            .replace('3', 'e')
            .replace('4', 'a')
            .replace('5', 's')
            .replace('7', 't')
            .replace('8', 'b')
            .replace('9', 'g')
            .replace('@', 'a')
            .replace('+', 't')
            .replace('!', 'i')
            .replace('|', 'i')
            .replace('$', 's')

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 6. Masking helper
    //    p******a  — first + last visible, middle masked
    //    Handles edge cases: 1-char, 2-char matches
    // ─────────────────────────────────────────────────────────────────────────

    private fun mask(matched: String): String = when {
        matched.isEmpty() -> ""
        matched.length == 1 -> "*"
        matched.length == 2 -> "${matched.first()}*"
        else -> "${matched.first()}${"*".repeat(matched.length - 2)}${matched.last()}"
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 7. Main filter function
    //    Returns Pair(wasCensored, censoredText)
    //
    //    Strategy:
    //      - Run each precompiled pattern against ORIGINAL text first
    //        (catches plain + uppercase variants without normalization)
    //      - If no match in original, run against NORMALIZED shadow
    //        (catches leet, repeated letters, separators)
    //      - When shadow matches, re-apply pattern to original to find
    //        the actual span to mask — falls back to shadow span if needed
    // ─────────────────────────────────────────────────────────────────────────

    // Checks if a matched span belongs to a protected word in the original text
    private fun isProtectedMatch(original: String, matchStart: Int, matchEnd: Int): Boolean {
        // Extract the full word surrounding the match by expanding to word boundaries
        var wordStart = matchStart
        var wordEnd   = matchEnd
        while (wordStart > 0 && original[wordStart - 1].isLetter()) wordStart--
        while (wordEnd < original.length && original[wordEnd].isLetter()) wordEnd++
        val fullWord = original.substring(wordStart, wordEnd).lowercase()
        return protectedWords.any { fullWord == it || fullWord.contains(it) }
    }

    fun censorText(original: String): Pair<Boolean, String> {
        if (original.isBlank()) return Pair(false, original)

        var censored = original
        var wasCensored = false
        // Lazy — only computed if a shadow match is needed
        val shadow: String by lazy { normalize(original) }

        for (compiled in compiledEntries) {
            val pattern = compiled.pattern

            // Pass 1 — try original text (handles plain + uppercase + mixed case)
            val matcherOriginal = pattern.matcher(censored)
            if (matcherOriginal.find()) {
                val sb = StringBuffer()
                val m = pattern.matcher(censored)
                var foundReal = false
                while (m.find()) {
                    // Skip if the match belongs to a protected word
                    if (isProtectedMatch(censored, m.start(), m.end())) {
                        m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(m.group()))
                        continue
                    }
                    foundReal = true
                    m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(mask(m.group())))
                }
                m.appendTail(sb)
                if (foundReal) {
                    wasCensored = true
                    censored = sb.toString()
                }
                continue
            }
            // Pass 2 — try shadow (handles leet, repeated chars, separators)
            val matcherShadow = pattern.matcher(shadow)
            if (matcherShadow.find()) {
                wasCensored = true
                // Shadow matched but we need to mask the ORIGINAL span
                // Strategy: use the shadow match position as a guide,
                // then mask the corresponding character range in the original
                // This works because normalize() only ever shortens or equals
                // the original length — never extends it
                val sb = StringBuffer()
                val mShadow = pattern.matcher(shadow)
                // Collect all shadow match ranges
                val shadowRanges = mutableListOf<IntRange>()
                while (mShadow.find()) {
                    shadowRanges.add(mShadow.start()..mShadow.end())
                }
                // Map shadow ranges back to original ranges using a char-level offset map
                val offsetMap = buildOffsetMap(original)
                var result = censored
                // Apply in reverse order to preserve indices
                for (range in shadowRanges.sortedByDescending { it.first }) {
                    val origStart = offsetMap.getOrNull(range.first) ?: continue
                    val origEnd   = offsetMap.getOrNull(range.last)?.plus(1)
                        ?: result.length
                    val span = result.substring(origStart, origEnd.coerceAtMost(result.length))
                    result = result.substring(0, origStart) +
                            mask(span) +
                            result.substring(origEnd.coerceAtMost(result.length))
                }
                censored = result
            }
        }

        return Pair(wasCensored, censored)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 8. Offset map builder
    //    Maps each index in the normalized shadow back to its position
    //    in the original string, accounting for characters removed by
    //    separator-stripping in normalization step 1.
    //
    //    Example:
    //      original:  "p . u . t . a"   (indices 0..12)
    //      shadow:    "puta"             (indices 0..3)
    //      map:       [0, 2, 4, 6]  — shadow[0]='p' came from original[0]
    // ─────────────────────────────────────────────────────────────────────────

    private fun buildOffsetMap(original: String): List<Int> {
        val map = mutableListOf<Int>()
        val lower = original.lowercase()
        val separatorRegex = Regex("[\\s.\\-_*|]")
        var i = 0
        while (i < lower.length) {
            val c = lower[i]
            // If this char would be stripped between word chars in normalization,
            // skip it in the map — otherwise record its original index
            val prevIsWord = if (i > 0) lower[i - 1].isLetterOrDigit() else false
            val nextIsWord = if (i < lower.length - 1) lower[i + 1].isLetterOrDigit() else false
            if (separatorRegex.matches(c.toString()) && prevIsWord && nextIsWord) {
                i++
                continue // this char was stripped in normalize()
            }
            map.add(i)
            i++
        }
        return map
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 9. Severity check helper — useful for future UI decisions
    // ─────────────────────────────────────────────────────────────────────────

    fun detectSeverity(text: String): Severity? {
        val shadow = normalize(text)
        var highest: Severity? = null
        for (compiled in compiledEntries) {
            if (compiled.pattern.matcher(text).find() ||
                compiled.pattern.matcher(shadow).find()) {
                if (compiled.entry.severity == Severity.STRONG) return Severity.STRONG
                highest = Severity.MILD
            }
        }
        return highest
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 10. Quick check — returns true if ANY profanity detected (no censoring)
    //     Use this for fast validation before saving to Firestore
    // ─────────────────────────────────────────────────────────────────────────

    fun containsProfanity(text: String): Boolean {
        if (text.isBlank()) return false
        val shadow = normalize(text)
        return compiledEntries.any { compiled ->
            compiled.pattern.matcher(text).find() ||
                    compiled.pattern.matcher(shadow).find()
        }
    }
}