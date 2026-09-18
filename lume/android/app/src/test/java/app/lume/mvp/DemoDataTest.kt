package app.lume.mvp

import org.junit.Assert.*
import org.junit.Test

class DemoDataTest {
    @Test fun examplesDistinguishPartialSupportedAndUnknown() {
        assertEquals(listOf(50, null, 90), DemoData.scenarios.map { it.score })
        assertEquals("Sem base para avaliar", DemoData.scenarios[1].verdict)
    }
    @Test fun missingOrInvalidCriteriaNeverProduceAScore() {
        val example = DemoData.scenarios[2]
        assertNull(example.copy(criteria = example.criteria.dropLast(1)).score)
        assertNull(example.copy(claim = " ").score)
        for (invalidLevel in listOf(-1, 3, 100)) {
            assertNull(example.copy(criteria = example.criteria.mapIndexed { i, c -> if (i == 0) c.copy(level = invalidLevel) else c }).score)
        }
        assertNull(example.copy(criteria = example.criteria.map { it.copy(reason = "") }).score)
        assertNull(example.copy(criteria = example.criteria.map { it.copy(weight = 40) }).score)
    }
    @Test fun sourceAndDirectEvidenceAreBothRequired() {
        for (index in listOf(0, 1)) {
            val example = DemoData.scenarios[2]
            val missing = example.copy(criteria = example.criteria.mapIndexed { i, c -> if (i == index) c.copy(level = 0) else c })
            assertNull(missing.score)
        }
    }
    @Test fun allRatingCombinationsStayBoundedAndEvidenceImprovementIsMonotonic() {
        val example = DemoData.scenarios[2]
        for (combination in 0 until 81) {
            var digits = combination
            val ratings = example.criteria.map { criterion ->
                criterion.copy(level = digits % 3).also { digits /= 3 }
            }
            val current = example.copy(criteria = ratings)
            val score = current.score ?: continue
            assertTrue(score in 0..100)
            for (index in ratings.indices) {
                val improved = current.copy(criteria = ratings.mapIndexed { i, c -> if (i == index) c.copy(level = 2) else c })
                assertTrue(improved.score!! >= score)
            }
        }
    }
    @Test fun demoBuildIsIsolatedFromConnectedVariant() {
        // The APK manifest is separately checked after packaging for the INTERNET permission.
        if (BuildConfig.FLAVOR == "demo") assertTrue(BuildConfig.DEMO_ONLY)
        if (BuildConfig.FLAVOR == "connected") assertFalse(BuildConfig.DEMO_ONLY)
    }
}
