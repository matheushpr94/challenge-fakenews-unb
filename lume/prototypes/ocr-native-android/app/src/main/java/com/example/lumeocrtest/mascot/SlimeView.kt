package com.example.lumeocrtest.mascot

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Deformação da gotinha. Todos os valores em repouso são 0.
 * - [squash] > 0 achata (mais larga e baixa), < 0 estica (mais alta e fina), preservando mais ou menos o volume;
 *   a barriga cede mais que a base e o topo, como um corpo macio.
 * - [lean] inclina o corpo a partir da base: o topo se desloca mais que o meio (curva, não cisalhamento reto).
 * - [lift] deixa a gotinha um pouco maior, como se estivesse "erguida" pelo dedo.
 * - [dy] desloca o desenho na vertical (px).
 */
data class SlimePose(val squash: Float = 0f, val lean: Float = 0f, val lift: Float = 0f, val dy: Float = 0f)

/** Malha que deforma a imagem: pura, testável sem tela. */
object SlimeMesh {
    const val COLS = 10
    const val ROWS = 12
    /** Base da gotinha na imagem (fração da altura), ponto que fica "no chão" ao achatar e inclinar. */
    const val BASE = 0.9f
    /** Altura aproximada do corpo na imagem (fração), do chão ao topo da ponta. */
    const val BODY = 0.84f

    /**
     * Vértices da imagem desenhada num quadrado de lado [d] com canto em ([left], [top]).
     * Devolve (COLS+1)*(ROWS+1) pares x,y.
     */
    fun deform(pose: SlimePose, left: Float, top: Float, d: Float, out: FloatArray = FloatArray((COLS + 1) * (ROWS + 1) * 2)): FloatArray {
        val ax = left + d / 2f
        val ay = top + d * BASE
        val grow = 1f + 0.05f * pose.lift
        var i = 0
        for (r in 0..ROWS) {
            val y0 = top + d * r / ROWS
            val h = ((ay - y0) / (d * BODY)).coerceIn(0f, 1.15f)
            // A barriga (meio da altura) cede mais que a base e a ponta.
            val belly = 0.55f + 0.75f * sin(PI.toFloat() * h.coerceAtMost(1f))
            val sx = 1f + pose.squash * 0.62f * belly
            val sy = 1f - pose.squash
            val bend = pose.lean * d * h.pow(1.7f)
            val sag = abs(pose.lean) * d * 0.12f * h * h
            for (c in 0..COLS) {
                val x0 = left + d * c / COLS
                out[i++] = ax + (x0 - ax) * sx * grow + bend
                out[i++] = ay + (y0 - ay) * sy * grow + sag + pose.dy
            }
        }
        return out
    }
}

/** A gotinha desenhada com malha deformável: mesmo desenho, mesmas cores; só a forma cede e volta. */
class SlimeView(context: Context, private var image: Bitmap, private val drawSize: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val verts = FloatArray((SlimeMesh.COLS + 1) * (SlimeMesh.ROWS + 1) * 2)
    var pose = SlimePose()
        set(value) { if (field != value) { field = value; invalidate() } }

    fun setImage(bitmap: Bitmap) { if (bitmap !== image) { image = bitmap; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        val left = (width - drawSize) / 2f
        val top = (height - drawSize) / 2f
        SlimeMesh.deform(pose, left, top, drawSize.toFloat(), verts)
        // Os vértices correspondem a pontos igualmente espaçados da imagem inteira, qualquer que seja a resolução dela.
        canvas.drawBitmapMesh(image, SlimeMesh.COLS, SlimeMesh.ROWS, verts, 0, null, 0, paint)
    }
}
