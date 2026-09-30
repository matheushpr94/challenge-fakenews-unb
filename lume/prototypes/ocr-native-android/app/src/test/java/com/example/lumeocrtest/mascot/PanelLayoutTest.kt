package com.example.lumeocrtest.mascot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Posição do balão: ao lado da gotinha, voltado para ela, sempre dentro da área útil. */
class PanelLayoutTest {
    private val areaW = 1080; private val areaH = 2200
    private val cardW = 680; private val cardH = 560
    private val tail = 30; private val margin = 22; private val corner = 55
    private fun place(x: Int, y: Int, w: Int = areaW, h: Int = areaH, cw: Int = cardW, ch: Int = cardH) =
        PanelLayout.place(x, y, 180, w, h, cw, ch, tail, margin, corner)
    private fun inside(p: PanelPlacement, w: Int = areaW, h: Int = areaH, cw: Int = cardW, ch: Int = cardH) =
        p.x >= 0 && p.y >= 0 && p.x + cw <= w && p.y + ch <= h

    @Test fun rightEdgeOpensToTheLeftPointingAtTheMascot() {
        val p = place(900, 700)
        assertEquals(TailSide.RIGHT, p.side)
        assertEquals(900 - tail - cardW, p.x)
        assertEquals(790, p.y + p.tailAt) // a ponta fica na altura do centro da gotinha
        assertTrue(inside(p))
    }

    @Test fun leftEdgeOpensToTheRight() {
        val p = place(0, 700)
        assertEquals(TailSide.LEFT, p.side)
        assertEquals(180 + tail, p.x)
        assertTrue(inside(p))
    }

    @Test fun nearTopOrBottomTheCardStaysOnScreenAndTheTailFollowsTheMascot() {
        val top = place(900, 0)
        assertEquals(margin, top.y); assertTrue(inside(top))
        assertTrue(top.tailAt >= corner + tail)
        val bottom = place(900, areaH - 180)
        assertEquals(areaH - cardH - margin, bottom.y); assertTrue(inside(bottom))
        assertTrue(bottom.tailAt <= cardH - corner - tail)
    }

    @Test fun narrowScreenGoesAboveOrBelow() {
        // Na tela estreita o cartão já vem limitado à largura útil (como no serviço).
        val below = place(0, 300, w = 700, cw = 620)
        assertEquals(TailSide.TOP, below.side); assertTrue(inside(below, w = 700, cw = 620))
        val above = place(520, 1900, w = 700, cw = 620)
        assertEquals(TailSide.BOTTOM, above.side); assertTrue(inside(above, w = 700, cw = 620))
    }

    @Test fun tallCardInLowLandscapeStillFits() {
        val p = place(2000, 400, w = 2260, h = 900, ch = 856)
        assertTrue(inside(p, w = 2260, h = 900, ch = 856))
    }
}
