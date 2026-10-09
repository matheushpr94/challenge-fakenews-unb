package com.example.lumeocrtest.research

import org.junit.Assert.assertEquals
import org.junit.Test

/** Confere a regressão em Kotlin contra a mesma conta feita em Python (2_comparar.py / 4_exportar_pesos.py). */
class PairModelTest {
    private val casos = listOf(
        Triple(0.7525191787618504, 0.9319296479225159, 0.7844889042768132) to 0.35714285714285715,
        Triple(0.8389808824562346, 0.9967869520187378, 0.9232772488362848) to 0.625,
        Triple(0.5033552157570201, 0.20862409472465515, 0.22632190201921443) to 0.25,
        Triple(0.7459390806809273, 0.9957072734832764, 0.8045666122764744) to 0.36363636363636365,
        Triple(0.024984780857396055, 0.0033452718053013086, 0.023044326819269337) to 0.047619047619047616,
        Triple(0.8671622959016452, 0.9965521097183228, 0.9274420867238448) to 0.6153846153846154,
        Triple(0.2770147259418687, 0.004340811632573605, 0.057817280708732835) to 0.1,
        Triple(0.6412985979906897, 0.339261919260025, 0.30290403403417937) to 0.15,
        Triple(0.4338109977111123, 0.7955410480499268, 0.41632856877437374) to 0.23809523809523808,
        Triple(0.23763046511250902, 0.0033030107151716948, 0.04565306914738109) to 0.058823529411764705,
    )

    @Test fun notaBateComPython() {
        casos.forEach { (t, sob) -> assertEquals(t.third, PairModel.nota(t.first, sob, t.second), 1e-6) }
    }

    @Test fun sobreposicaoIgnoraAcentoEPalavrasCurtas() {
        assertEquals(1.0, PairModel.sobreposicao("Vacina é aplicada", "vacina aplicada"), 1e-9)
        assertEquals(0.0, PairModel.sobreposicao("de em", "um a"), 1e-9)
    }
}
