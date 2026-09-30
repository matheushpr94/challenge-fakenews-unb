package com.example.lumeocrtest.ocr

import java.util.Calendar
import java.util.TimeZone

/**
 * Interpreta UMA linha curta candidata a assinatura/data. Reconhece formas gerais, não nomes de veículos:
 *  - "Por Nome Sobrenome, Veículo — Cidade"      (autor individual + veículo + local)
 *  - "Da Organização | 28/09/2026, 10h51"         (assinatura institucional + data)
 *  - "NOME SOBRENOME - REPÓRTER DA ORGANIZAÇÃO"   (autor + função + veículo)
 *  - "Da Organização em Cidade"                  (veículo + local)
 *  - "Publicado em 25/09/2026 - 20:33", "29 setembro 2026, 06:10", "Atualizado há 7 horas"
 * Campos sem evidência ficam nulos.
 */
data class BylineParse(
    val author: String? = null,
    val organization: String? = null,
    val date: String? = null,
    val dateMs: Long? = null,
    val updated: String? = null,
    val place: String? = null,
    val role: String? = null,
    /** A linha é só um nome próprio (pode ser autor se estiver junto de uma assinatura). */
    val bareName: String? = null,
) {
    val isEmpty get() = author == null && organization == null && date == null && updated == null && bareName == null
    val isByline get() = author != null || organization != null || date != null
}

object BylineParser {
    private const val MONTHS = "janeiro|fevereiro|março|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro"
    private const val MONTHS_SHORT = "jan|fev|mar|abr|mai|jun|jul|ago|set|out|nov|dez"
    private val MONTH_NUM = listOf("jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez")
    private val DATE_NUM = Regex("""(?<!\d)(\d{1,2})[/.](\d{1,2})[/.](\d{2,4})(?!\d)""")
    private val DATE_TXT = Regex("""(?iu)(?<!\d)(\d{1,2})º?\s+(?:de\s+)?($MONTHS|$MONTHS_SHORT)\.?\s+(?:de\s+)?(\d{4})""")
    private val TIME = Regex("""(?iu)(?<!\d)(\d{1,2})(?:h(\d{2})?|:(\d{2}))(?:min)?(?![\p{L}\d])""")
    private val RELATIVE = Regex("""(?iu)\b(?:há|ha)\s+\d+\s+(?:minutos?|min|horas?|h|dias?)\b""")
    private val PUBLISHED = Regex("""(?iu)^(?:publicad[oa]|publicação|postad[oa])(?:\s+em)?\s*:?\s*""")
    private val UPDATED = Regex("""(?iu)^(?:atualizad[oa]|última atualização|atualização)(?:\s+em)?\s*:?\s*""")
    private val AUTHOR_MARK = Regex("""(?iu)^(?:por|by|escrito por|texto(?: de)?|reportagem(?: de)?|autor(?:a|ia)?)\s*:?\s+(.+)$""")
    private val ORG_MARK = Regex("""(?u)^(?:[Dd][aoe]s?)\s+(.+)$""")
    private val ROLE = Regex("""(?iu)\b(repórter|reporter|colunista|correspondente|enviad[oa] especial|editor[a]?|colaboração|especial|estagiári[oa]|analista)\s*(?:para|d[aoe]s?)?\s+(.+)$""")
    private val PLACE_TAIL = Regex("""(?u)\s+(?:em|no|na)\s+(\p{Lu}[\p{L}]+(?:\s+(?:d[aoe]s?\s+)?\p{Lu}[\p{L}]+){0,2})$""")
    private val SEPARATORS = Regex("""\s*[|·•]\s*|\s+[-–—]\s+|\s*[-–—]\s*(?=\d)|(?<=\d)\s*[-–—]\s*|\s+(?=(?iu:atualizad|última atualização))""")
    private val TZ_SUFFIX = Regex("""\s+[-+]\d{2}(?::?\d{2})?$""")
    /** Palavras que indicam organização (genéricas, sem nomes de veículos). */
    val ORG_WORDS = Regex("""(?iu)(?<![\p{L}])(agência|agencia|redação|redacao|jornal|portal|revista|rádio|radio|tv|news|notícias|noticias|editoria|assessoria|conteúdo|estúdio|editora|diário|diario|gazeta|folha|correio|tribuna|site|blog|canal)(?![\p{L}])""")
    private val GENERIC_ORG = Regex("""(?iu)^(?:a\s+)?(?:redação|redacao|equipe|editoria|da redação)$""")
    private val NOT_NAME_WORDS = Regex("""(?iu)(?<![\p{L}])(apoie|assine|inscreva|compartilhe|clique|publicado|atualizado|conteúdo|leia|veja|mais|menu|foto|imagem|crédito|reprodução|divulgação|arquivo|compartilhar|seguir|siga|entrar|buscar|notícias|economia|política|esportes|mundo|brasil|segurança|saúde|cultura|tecnologia|educação|ciência|opinião)(?![\p{L}])""")

    /** OCR troca "I" maiúsculo por "l" no início de palavra ("lgor"): em português, "l" não vem antes de consoante. */
    private val OCR_CAPITAL_I = Regex("""(?<![\p{L}])l(?=[bcdfgjkmnpqrstvwxz][a-zà-ÿ])""")
    /** "Nome Sobrenome Do Veículo, no Local": o separador visual (ponto, barra) às vezes some no OCR. */
    private val NAME_THEN_ORG = Regex("""^(.+?)\s+((?:Do|Da|Dos|Das)\s+\p{Lu}.*)$""")
    private val BEFORE_DATE = Regex("""(?<![Ee]m)(?<![:|,])\s+(?=\d{1,2}/\d{1,2}/\d{2,4})""")

    fun parse(raw: String): BylineParse {
        var text = TZ_SUFFIX.replace(raw.replace(Regex("\\s+"), " ").trim().trimEnd('>', '›', '»', ' '), "")
        text = OCR_CAPITAL_I.replace(text, "I")
        // Data colada ao resto da linha ("... Rio de Janeiro 29/09/2026 22h03").
        text = BEFORE_DATE.replace(text, " | ")
        NAME_THEN_ORG.find(text.substringBefore(" | "))?.let { m ->
            if (isPersonName(m.groupValues[1])) text = text.replaceFirst(m.value, "${m.groupValues[1]} | ${m.groupValues[2]}")
        }
        if (text.length > 140 || text.isEmpty()) return BylineParse()
        var author: String? = null
        var org: String? = null
        var date: String? = null
        var dateMs: Long? = null
        var updated: String? = null
        var place: String? = null
        var role: String? = null

        // Data e hora podem aparecer em qualquer segmento.
        val dateMatch = DATE_NUM.find(text) ?: DATE_TXT.find(text)
        val updatedPrefix = UPDATED.find(text)
        val segments = text.split(SEPARATORS).map { it.trim().trim(',', ';', ':', '.').trim() }.filter { it.isNotEmpty() }
        val dateSegments = mutableListOf<String>()
        val rest = mutableListOf<String>()
        for (seg in segments) {
            val s = seg
            val isUpdate = UPDATED.containsMatchIn(s) || (RELATIVE.containsMatchIn(s) && !DATE_NUM.containsMatchIn(s))
            when {
                isUpdate -> updated = UPDATED.replace(s, "").trim().ifEmpty { s }
                PUBLISHED.containsMatchIn(s) || DATE_NUM.containsMatchIn(s) || DATE_TXT.containsMatchIn(s) ||
                    (TIME.matches(s.trim()) && (dateSegments.isNotEmpty() || dateMatch != null)) ||
                    Regex("(?iu)^-?\\d{2}(?::?\\d{2})?$").matches(s) && dateSegments.isNotEmpty() ->
                    dateSegments.add(PUBLISHED.replace(s, "").trim())
                else -> rest.add(s)
            }
        }
        if (dateSegments.isNotEmpty() && dateMatch != null && updatedPrefix?.range?.first != 0) {
            date = dateSegments.joinToString(", ").replace(Regex(",\\s*,"), ",")
            dateMs = dateMillis(dateMatch)
        }
        for (seg0 in rest) {
            var seg = seg0
            // "Nome Sobrenome, Veículo" (vírgula separa autor e veículo).
            val authorMark = AUTHOR_MARK.find(seg)
            if (authorMark != null && author == null && org == null) {
                val parts = authorMark.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val who = parts.first()
                if (ORG_WORDS.containsMatchIn(who) || GENERIC_ORG.matches(who)) org = cleanOrg(who)
                else if (isPersonName(who)) author = normalizeName(who)
                parts.drop(1).firstOrNull()?.let { tail ->
                    // Depois da vírgula: veículo quando tem cara de marca/organização; senão é o local ("Por X, Brasília").
                    val brandLike = ORG_WORDS.containsMatchIn(tail) || tail.any(Char::isDigit) ||
                        Regex("^[A-Z]{2,6}$").matches(tail) || Regex("^[a-z][\\w.]{1,15}$").matches(tail)
                    if (DATE_NUM.containsMatchIn(tail) || tail.split(" ").size > 5) Unit
                    else if (org == null && brandLike) org = cleanOrg(tail)
                    else if (place == null && isPlace(tail.removePrefix("de ").removePrefix("em "))) place = tail.removePrefix("de ").removePrefix("em ")
                }
                continue
            }
            val roleMatch = ROLE.find(seg)
            if (roleMatch != null) {
                role = roleMatch.groupValues[1].lowercase()
                org = org ?: cleanOrg(roleMatch.groupValues[2])
                continue
            }
            val orgMatch = ORG_MARK.find(seg)
            if (orgMatch != null && org == null) {
                var body = orgMatch.groupValues[1].trim()
                PLACE_TAIL.find(body)?.let { m ->
                    if (body.substring(0, m.range.first).split(" ").size >= 1) {
                        place = m.groupValues[1]
                        body = body.substring(0, m.range.first).trim()
                    }
                }
                if (isOrgName(body)) { org = cleanOrg(body); continue }
            }
            if (author == null && org == null && segments.size > 1 && isPersonName(seg) &&
                rest.any { r -> ROLE.containsMatchIn(r) || ORG_MARK.find(r)?.let { m -> isOrgName(PLACE_TAIL.replace(m.groupValues[1], "").trim().trim(',')) } == true }) {
                author = normalizeName(seg); continue
            }
            // Segmento isolado após o autor: cidade ("— Brasília").
            if ((author != null || org != null) && place == null && isPlace(seg)) { place = seg; continue }
        }
        // "Atualizado Hi 5 horas" (erro de leitura): só mantém quando é data ou "há N minutos/horas/dias".
        if (updated != null && !RELATIVE.containsMatchIn(updated!!) && !DATE_NUM.containsMatchIn(updated!!) &&
            !DATE_TXT.containsMatchIn(updated!!)) updated = null
        val bare = if (author == null && org == null && date == null && updated == null && isPersonName(text) &&
            !ORG_WORDS.containsMatchIn(text)) normalizeName(text) else null
        return BylineParse(author, org, date, dateMs, updated, place, role, bare)
    }

    fun isPlace(text: String): Boolean {
        val words = text.split(" ")
        return words.size in 1..3 && words.all { w -> w.first().isUpperCase() && w.all { it.isLetter() || it == '-' } } &&
            !ORG_WORDS.containsMatchIn(text)
    }

    private fun cleanOrg(s: String): String? {
        val t = s.trim().trim(',', '.', '-', '|', ' ').replace(Regex("\\s+"), " ")
        if (t.isEmpty() || t.length > 60 || DATE_NUM.containsMatchIn(t)) return null
        return if (t.count { it.isUpperCase() } > t.count { it.isLetter() } * 0.7 && t.length > 4) titleCase(t) else t
    }

    private fun isOrgName(s: String): Boolean {
        val words = s.split(" ").filter { it.isNotEmpty() }
        if (words.isEmpty() || words.size > 6 || DATE_NUM.containsMatchIn(s) || s.length > 60) return false
        if (GENERIC_ORG.matches(s)) return true
        val first = words.first()
        return first.first().isUpperCase() || Regex("^[a-z]{1,3}\\d$").matches(first)
    }

    fun isPersonName(text: String): Boolean {
        val t = text.trim().trimEnd('>', '›', '»').trim()
        if (t.length !in 5..60 || t.any { it.isDigit() } || NOT_NAME_WORDS.containsMatchIn(t)) return false
        val words = t.split(Regex("\\s+"))
        if (words.size !in 2..5) return false
        if (!words.all { w -> w.all { it.isLetter() || it == '-' || it == '\'' || it == '’' || it == '.' } }) return false
        val proper = words.filter { it.lowercase() !in setOf("da", "de", "do", "das", "dos", "e") }
        return proper.size >= 2 && proper.all { it.first().isUpperCase() }
    }

    fun normalizeName(s: String): String {
        val t = s.trim().trimEnd('>', '›', '»').trim().replace(Regex("\\s+"), " ")
        val letters = t.filter { it.isLetter() }
        return if (letters.isNotEmpty() && letters.count { it.isUpperCase() } >= letters.length * 0.6) titleCase(t) else t
    }

    private fun titleCase(s: String) = s.lowercase().split(" ").joinToString(" ") { w ->
        if (w in setOf("da", "de", "do", "das", "dos", "e")) w else w.replaceFirstChar { it.uppercase() }
    }

    private fun dateMillis(m: MatchResult): Long? = runCatching {
        val g = m.groupValues
        val day = g[1].toInt()
        val month = g[2].toIntOrNull() ?: (MONTH_NUM.indexOf(g[2].lowercase().take(3).replace("ç", "c")) + 1)
        var year = g[3].toInt()
        if (year < 100) year += 2000
        if (day !in 1..31 || month !in 1..12 || year !in 1990..2100) return null
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(year, month - 1, day) }.timeInMillis
    }.getOrNull()
}
