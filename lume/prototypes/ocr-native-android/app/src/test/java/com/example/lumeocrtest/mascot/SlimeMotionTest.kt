package com.example.lumeocrtest.mascot

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SlimeMotionTest {
    private fun run(s: Spring, seconds: Float, fps: Int = 60, onStep: (Float) -> Unit = {}) {
        repeat((seconds * fps).toInt()) { s.step(1f / fps); onStep(s.value) }
    }

    @Test fun softSpringOvershootsOnceOrTwiceAndSettles() {
        val s = Spring(0f, 170f, 0.52f, target = 100f)
        var peak = 0f
        run(s, 2f) { peak = maxOf(peak, it) }
        assertTrue("passa do lugar (efeito mola): $peak", peak in 105f..125f)
        assertTrue(s.atRest(0.5f))
        assertEquals(100f, s.value, 0.5f)
    }

    @Test fun criticallyDampedSpringNeverOvershoots() {
        val s = Spring(0f, 300f, 1f, target = 1f)
        run(s, 1.5f) { assertTrue(it <= 1.0005f) }
        assertEquals(1f, s.value, 0.001f)
    }

    @Test fun springIsStableAtLowFrameRates() {
        val s = Spring(0f, 900f, 0.3f, target = 1f)
        run(s, 3f, fps = 20) { assertTrue(abs(it) < 3f) }
        assertEquals(1f, s.value, 0.01f)
    }

    @Test fun restPoseDrawsTheImageUnchanged() {
        val v = SlimeMesh.deform(SlimePose(), 10f, 20f, 100f)
        val expected = FloatArray(v.size)
        var i = 0
        for (r in 0..SlimeMesh.ROWS) for (c in 0..SlimeMesh.COLS) {
            expected[i++] = 10f + 100f * c / SlimeMesh.COLS; expected[i++] = 20f + 100f * r / SlimeMesh.ROWS
        }
        assertArrayEquals(expected, v, 0.001f)
    }

    @Test fun squashKeepsTheBaseOnTheGroundAndWidensTheBelly() {
        val d = 100f
        val rest = SlimeMesh.deform(SlimePose(), 0f, 0f, d)
        val flat = SlimeMesh.deform(SlimePose(squash = 0.15f), 0f, 0f, d)
        fun y(v: FloatArray, r: Int, c: Int) = v[(r * (SlimeMesh.COLS + 1) + c) * 2 + 1]
        fun x(v: FloatArray, r: Int, c: Int) = v[(r * (SlimeMesh.COLS + 1) + c) * 2]
        val baseRow = Math.round(SlimeMesh.BASE * SlimeMesh.ROWS)
        assertEquals(y(rest, baseRow, 5), y(flat, baseRow, 5), 1.5f)
        assertTrue("o topo desce", y(flat, 0, 5) > y(rest, 0, 5) + 10f)
        val mid = SlimeMesh.ROWS / 2
        assertTrue("a barriga alarga", x(flat, mid, 0) < x(rest, mid, 0) - 3f)
    }

    @Test fun leanBendsTheTopMoreThanTheBase() {
        val v = SlimeMesh.deform(SlimePose(lean = 0.1f), 0f, 0f, 100f)
        val r = SlimeMesh.deform(SlimePose(), 0f, 0f, 100f)
        fun dx(row: Int) = v[(row * (SlimeMesh.COLS + 1) + 5) * 2] - r[(row * (SlimeMesh.COLS + 1) + 5) * 2]
        val baseRow = Math.round(SlimeMesh.BASE * SlimeMesh.ROWS)
        assertTrue(dx(1) > dx(SlimeMesh.ROWS / 2)); assertTrue(dx(SlimeMesh.ROWS / 2) > dx(baseRow))
        assertEquals(0f, dx(baseRow), 0.5f)
    }

    @Test fun strongestPosesStayInsideTheWindow() {
        // Janela de 92 dp com o desenho de 76 dp. O corpo ocupa o miolo da imagem (colunas 2..8); as bordas dela são
        // transparentes. Nos limites de deformação, nenhum ponto do corpo pode sair da janela.
        val d = 76f; val w = 92f; val m = (w - d) / 2
        for (p in listOf(SlimePose(squash = SQUASH_MAX), SlimePose(squash = SQUASH_MIN, lift = LIFT_MAX),
            SlimePose(lean = LEAN_MAX, lift = LIFT_MAX), SlimePose(lean = -LEAN_MAX, squash = SQUASH_MIN, lift = LIFT_MAX))) {
            val v = SlimeMesh.deform(p, m, m, d)
            for (r in 1 until SlimeMesh.ROWS) for (c in 2..SlimeMesh.COLS - 2) {
                val x = v[(r * (SlimeMesh.COLS + 1) + c) * 2]; val y = v[(r * (SlimeMesh.COLS + 1) + c) * 2 + 1]
                assertTrue("$p ($r,$c) = $x,$y", x in 0f..w && y in 0f..w)
            }
        }
    }
}
