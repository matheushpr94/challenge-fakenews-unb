package com.example.lumeocrtest.research

/*
 * A notícia importada não é fonte independente dela mesma. Compara endereço canônico, veículo (nome e domínio)
 * e título com a matéria lida na captura. Matérias de OUTROS veículos continuam independentes mesmo com títulos
 * parecidos; só o título idêntico ao da captura (ou crédito explícito ao veículo da captura) indica republicação.
 */

object Independence {
    const val PROPRIA = "propria"            // a própria matéria importada
    const val MESMO_VEICULO = "mesmo_veiculo" // outra publicação do veículo da captura
    const val REPUBLICACAO = "republicacao"   // o texto da captura publicado por outro veículo
}

data class IndependenceCheck(val kind: String, val motivo: String)

/** Chave do veículo: nome sem acentos, espaços e artigo inicial ("O Globo" -> "oglobo", "UOL Notícias" -> "uolnoticias"). */
fun vehicleKey(name: String?): String? = norm(name).replace(" ", "").takeIf { it.length >= 2 }

private fun domainLabels(domain: String): List<String> = domain.lowercase().removePrefix("www.").split('.')
    .filter { it.isNotEmpty() && it !in setOf("com", "br", "org", "net", "gov", "leg", "jus", "www") }

/** O candidato é do mesmo veículo da captura (pelo nome informado pelo buscador ou pelo domínio)? */
fun sameVehicle(vehicle: String?, cand: Candidate): Boolean {
    val key = vehicleKey(vehicle) ?: return false
    val candKey = vehicleKey(cand.sourceName)
    if (candKey != null && (candKey == key || key.length >= 3 && candKey.startsWith(key) || candKey.length >= 3 && key.startsWith(candKey))) return true
    val labels = domainLabels(cand.domain.ifBlank { domainOf(cand.url) })
    return labels.any { it == key || key.length >= 4 && it.replace("-", "") == key }
}

/** Título igual (a menos de pontuação/uma palavra) e com os mesmos números. */
private fun sameTitle(a: String, b: String): Boolean {
    if (numbersIn(a) != numbersIn(b)) return false
    val ka = titleKey(a); val kb = titleKey(b)
    if (ka.size < 4 || kb.size < 4) return norm(a) == norm(b)
    return jaccard(ka, kb) >= 0.85
}

/** Republicação sai perto da original: publicação muito distante da data da matéria importada é outro texto. */
private fun closeInTime(article: ArticleContext, cand: Candidate): Boolean {
    val a = article.publishedAtMs ?: return true
    val c = cand.published ?: return true
    return Math.abs(c - a) <= 7 * Dates.DAY_MS
}

private fun creditsVehicle(page: String, vehicle: String): Boolean {
    val v = Regex.escape(vehicle.trim())
    return Regex("(?iu)(?:$v\\s+Conteúdo|Fonte:\\s*$v|com informações d[oa]s?\\s+$v|Publicado originalmente (?:no|na|em)\\s+$v)").containsMatchIn(page)
}

/** null quando a fonte é independente da matéria importada. */
fun independenceOf(article: ArticleContext?, cand: Candidate): IndependenceCheck? {
    if (article == null) return null
    article.url?.let { u -> if (canonical(u) == canonical(cand.url)) return IndependenceCheck(Independence.PROPRIA, "mesmo endereço da matéria importada") }
    val vehicle = article.source
    val titleMatch = article.title?.let { sameTitle(it, cand.title) } == true && closeInTime(article, cand)
    if (sameVehicle(vehicle, cand)) {
        return if (titleMatch) IndependenceCheck(Independence.PROPRIA, "mesmo veículo e mesmo título da matéria importada")
        else IndependenceCheck(Independence.MESMO_VEICULO, "publicado pelo mesmo veículo da matéria importada ($vehicle)")
    }
    if (titleMatch) {
        return IndependenceCheck(Independence.REPUBLICACAO, if (vehicle.isNullOrBlank())
            "mesmo título da matéria importada: pode ser a própria matéria ou uma republicação (o veículo da captura não foi identificado)"
        else "mesmo título da matéria importada, publicado por outro veículo (republicação)")
    }
    if (!vehicle.isNullOrBlank() && cand.pageText.isNotBlank() && creditsVehicle(cand.pageText, vehicle)) {
        return IndependenceCheck(Independence.REPUBLICACAO, "a página credita o texto a $vehicle")
    }
    return null
}
