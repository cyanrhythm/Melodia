package com.lin0721.linmusic.core.player.domain

// QRC 逐字歌词文本解析为 [LyricLine] 列表
object QrcLyricParser {

    private val lyricContentAttrRegex = Regex("""LyricContent="([^"]*)"""")
    private val lyricContentTagRegex = Regex("""<LyricContent>([\s\S]*?)</LyricContent>""")

    private val metaTagRegex = Regex("""^\[(ti|ar|al|by|offset|kana|romaji):""", RegexOption.IGNORE_CASE)
    private val qrcLineHeaderRegex = Regex("""^\[(\d+),(\d+)](.*)$""")
    private val lrcLineHeaderRegex = Regex("""^\[(\d{2,}):(\d{2})[.:](\d{2,3})](.*)$""")
    private val qrcWordRegex = Regex("""(.*?)\((\d+),(\d+)\)""")
    private val wordTagStripRegex = Regex("""\(\d+,\d+\)""")

    fun parse(rawText: String): List<LyricLine> {
        if (rawText.isBlank()) return emptyList()
        val content = extractLyricContent(rawText)
        return content.lines().mapNotNull(::parseLine).sortedBy { it.timeMs }
    }

    private fun extractLyricContent(raw: String): String {
        val trimmed = raw.trim()
        lyricContentAttrRegex.find(trimmed)?.let { return decodeXmlEntities(it.groupValues[1]) }
        lyricContentTagRegex.find(trimmed)?.let { return decodeXmlEntities(it.groupValues[1]) }
        return if (trimmed.startsWith("<")) decodeXmlEntities(trimmed) else raw
    }

    private fun parseLine(rawLine: String): LyricLine? {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty() || metaTagRegex.containsMatchIn(trimmed)) return null

        val (lineStartTime, declaredDuration, body) = parseHeader(trimmed) ?: return null
        val matches = qrcWordRegex.findAll(body).toList()

        if (matches.isEmpty()) {
            val plain = body.replace(wordTagStripRegex, "").trim()
            if (plain.isEmpty()) return null
            return LyricLine(timeMs = lineStartTime, durationMs = declaredDuration, text = plain)
        }

        val wordsList = mutableListOf<WordInfo>()
        val fullTextBuilder = StringBuilder()

        for (match in matches) {
            val wordText = match.groupValues[1]
            val absoluteStart = match.groupValues[2].toLongOrNull() ?: 0L
            val duration = match.groupValues[3].toLongOrNull() ?: 0L

            if (wordText.isEmpty() && duration == 0L) continue

            val startOffset = if (absoluteStart >= lineStartTime) absoluteStart - lineStartTime else absoluteStart.coerceAtLeast(0L)
            wordsList.add(WordInfo(wordText, startOffset, duration))
            fullTextBuilder.append(wordText)
        }

        val lastEnd = matches.last().range.last
        if (lastEnd + 1 < body.length) {
            val trailing = body.substring(lastEnd + 1)
            if (trailing.isNotBlank()) {
                fullTextBuilder.append(trailing)
            }
        }

        val fullText = fullTextBuilder.toString().trim()
        if (fullText.isEmpty()) return null

        val lineDuration = if (declaredDuration > 0) {
            declaredDuration
        } else {
            wordsList.lastOrNull()?.let { it.startOffsetMs + it.durationMs } ?: 0L
        }

        return LyricLine(
            timeMs = lineStartTime,
            durationMs = lineDuration,
            text = fullText,
            words = wordsList
        )
    }

    private fun parseHeader(line: String): Triple<Long, Long, String>? {
        qrcLineHeaderRegex.find(line)?.let { match ->
            val start = match.groupValues[1].toLongOrNull() ?: return null
            val dur = match.groupValues[2].toLongOrNull() ?: 0L
            return Triple(start, dur, match.groupValues[3])
        }

        lrcLineHeaderRegex.find(line)?.let { match ->
            val min = match.groupValues[1].toLongOrNull() ?: return null
            val sec = match.groupValues[2].toLongOrNull() ?: return null
            val msRaw = match.groupValues[3]
            val ms = msRaw.toLongOrNull() ?: return null
            val timeMs = min * 60_000 + sec * 1000 + if (msRaw.length == 2) ms * 10 else ms
            return Triple(timeMs, 0L, match.groupValues[4])
        }

        return null
    }

    private fun decodeXmlEntities(str: String): String {
        return str
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace(Regex("&#(\\d+);")) { match ->
                val code = match.groupValues[1].toIntOrNull()
                codePointToString(code)
            }
            .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
                val code = match.groupValues[1].toIntOrNull(16)
                codePointToString(code)
            }
    }

    private fun codePointToString(code: Int?): String {
        if (code == null || code !in 1..0x10FFFF) return ""
        return if (code <= 0xFFFF) {
            code.toChar().toString()
        } else {
            val high = ((code - 0x10000) ushr 10) + 0xD800
            val low = ((code - 0x10000) and 0x3FF) + 0xDC00
            charArrayOf(high.toChar(), low.toChar()).concatToString()
        }
    }
}
