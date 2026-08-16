package com.langualens.app.translate

/** Sentence splitting tuned for Dutch, with the common abbreviations guarded. */
object Sentences {

    private val ABBREVIATIONS = setOf(
        "dhr", "mevr", "mw", "bijv", "bv", "o.a", "d.w.z", "m.a.w", "a.u.b", "z.o.z",
        "enz", "nr", "art", "blz", "ca", "dr", "prof", "ir", "drs", "mr", "st",
        "nl", "zgn", "evt", "tel", "fig", "incl", "excl", "max", "min", "vs", "etc",
        "jr", "sr", "t.o.v", "i.p.v", "e.d", "ong", "resp", "pag", "hfdst"
    )

    fun split(input: String): List<String> {
        val text = input.replace('\u00A0', ' ').trim()
        if (text.isEmpty()) return emptyList()

        val out = ArrayList<String>()
        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            sb.append(c)
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                // consume trailing quotes/brackets that belong to this sentence
                var j = i + 1
                while (j < text.length && (text[j] == '"' || text[j] == '”' ||
                            text[j] == '\'' || text[j] == ')' || text[j] == '»' ||
                            text[j] == '.' || text[j] == '!' || text[j] == '?')
                ) {
                    sb.append(text[j]); j++
                }
                val next = if (j < text.length) text[j] else null
                val boundary = next == null || next == ' ' || next == '\n' || next == '\t'
                if (boundary && !endsWithAbbreviation(sb) && !endsWithInitial(sb)) {
                    val s = sb.toString().trim()
                    if (s.isNotEmpty()) out.add(s)
                    sb.setLength(0)
                }
                i = j
            } else {
                i++
            }
        }
        val tail = sb.toString().trim()
        if (tail.isNotEmpty()) out.add(tail)
        return out.filter { it.any { ch -> ch.isLetter() } }
    }

    private fun endsWithAbbreviation(sb: StringBuilder): Boolean {
        val s = sb.toString().trimEnd()
        if (!s.endsWith(".")) return false
        val body = s.dropLast(1)
        val lastToken = body.takeLastWhile { !it.isWhitespace() }.lowercase()
        return ABBREVIATIONS.contains(lastToken)
    }

    /** "J. Bakker" style initials should not end a sentence. */
    private fun endsWithInitial(sb: StringBuilder): Boolean {
        val s = sb.toString().trimEnd()
        if (!s.endsWith(".")) return false
        val body = s.dropLast(1)
        val lastToken = body.takeLastWhile { !it.isWhitespace() }
        return lastToken.length == 1 && lastToken[0].isUpperCase()
    }

    fun looksLikeSingleWord(text: String): Boolean {
        val t = text.trim()
        return t.isNotEmpty() && t.split(Regex("\\s+")).size <= 2 && !t.contains('.')
    }
}
