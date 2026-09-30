package com.example.lumeocrtest.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class MascotTimingTest {
    @Test fun snapsToTheNearestEdge() {
        assertEquals(0, MascotTiming.snapTarget(10, 900))
        assertEquals(0, MascotTiming.snapTarget(449, 900))
        assertEquals(900, MascotTiming.snapTarget(450, 900))
        assertEquals(900, MascotTiming.snapTarget(900, 900))
    }

    @Test fun idleMovesAreSparseAndCalmerAfterAWhile() {
        val r = Random(7)
        repeat(200) {
            val blink = MascotTiming.nextBlinkDelay(r, calm = false)
            val breath = MascotTiming.nextBreathDelay(r, calm = false)
            assertTrue(blink in 3_500L..8_000L)
            // Entre uma respiração e outra há sempre uma pausa maior que a própria respiração.
            assertTrue(breath >= 6_000L && breath > 2 * MascotTiming.BREATH_MS)
            assertTrue(MascotTiming.nextBlinkDelay(r, calm = true) >= 7_000L)
            assertTrue(MascotTiming.nextBreathDelay(r, calm = true) >= 20_000L)
        }
        // Parada por muito tempo, só pisca.
        assertTrue(MascotTiming.breathes(60_000L))
        assertTrue(!MascotTiming.breathes(MascotTiming.REST_AFTER_MS + 1))
    }
}
