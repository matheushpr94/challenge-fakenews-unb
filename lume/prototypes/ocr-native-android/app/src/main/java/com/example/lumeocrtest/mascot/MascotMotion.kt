package com.example.lumeocrtest.mascot

import android.graphics.Bitmap
import android.os.Handler
import android.os.SystemClock
import android.view.Choreographer
import android.view.View
import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.sqrt
import kotlin.random.Random

/** Regras de tempo e posição da gotinha, sem dependência de tela (testáveis). */
object MascotTiming {
    const val BREATH_MS = 2600L
    const val BLINK_MS = 130L
    /** Sem toque por este tempo, a gotinha fica mais quieta: respira menos e pisca com menos frequência. */
    const val CALM_AFTER_MS = 90_000L
    /** Depois disso, só pisca de vez em quando: nada de animação quadro a quadro enquanto ninguém interage. */
    const val REST_AFTER_MS = 5 * 60_000L

    /** Borda mais próxima: 0 (esquerda) ou [maxX] (direita). */
    fun snapTarget(x: Int, maxX: Int): Int = if (x < maxX / 2) 0 else maxX

    fun nextBlinkDelay(random: Random, calm: Boolean): Long =
        if (calm) random.nextLong(7_000, 14_000) else random.nextLong(3_500, 8_000)

    fun nextBreathDelay(random: Random, calm: Boolean): Long =
        if (calm) random.nextLong(20_000, 30_000) else random.nextLong(6_000, 10_000)

    /** Respira só nos primeiros minutos depois de uma interação. */
    fun breathes(sinceInteractionMs: Long) = sinceInteractionMs < REST_AFTER_MS
}

/**
 * Mola amortecida (massa 1). [stiffness] define a rapidez; [damping] < 1 deixa passar do alvo e voltar (o "boing"),
 * 1 chega sem passar. Integração semi-implícita em passos curtos: estável em qualquer taxa de quadros.
 */
class Spring(var value: Float, var stiffness: Float, var damping: Float, var target: Float = value) {
    var velocity = 0f
    fun set(stiffness: Float, damping: Float) { this.stiffness = stiffness; this.damping = damping }
    fun step(dt: Float) {
        var left = dt
        val c = 2f * damping * sqrt(stiffness)
        while (left > 0f) {
            val h = minOf(left, 1f / 240f)
            velocity += (-stiffness * (value - target) - c * velocity) * h
            value += velocity * h
            left -= h
        }
    }
    fun atRest(eps: Float) = abs(velocity) < eps * 8f && abs(value - target) < eps
    fun snap(v: Float) { value = v; target = v; velocity = 0f }
}

/**
 * Linguagem de movimento da gotinha, como um corpo de slime macio:
 * - entrada: espia pela borda, se encolhe para tomar impulso, sai esticada, passa um pouco do lugar,
 *   achata ao "frear" e balança até se acomodar, com uma piscada no fim;
 * - toque: cede na hora ao dedo e volta com um pequeno balanço; arraste: fica erguida, o corpo atrasa em relação
 *   ao dedo e estica na direção do movimento; soltar: desliza até a borda com a velocidade do gesto e amassa ao
 *   encostar nela;
 * - painel: inclina de leve na direção dele enquanto está aberto e volta balançando ao fechar;
 * - repouso: uma respiração lenta de vez em quando e piscadas; depois de alguns minutos, só pisca.
 *
 * Só há quadros enquanto algo se move: um laço do Choreographer que para sozinho quando todas as molas assentam.
 * Nada roda com a gotinha oculta, com a tela desligada ou depois de [stop]. Com animações reduzidas no Android
 * (ou desligadas no app), tudo vai direto ao estado final. A duração respeita a escala de animação do sistema.
 */
/** Limites da deformação: cabem na folga da janela (92 dp para um desenho de 76 dp). */
const val SQUASH_MIN = -0.15f
const val SQUASH_MAX = 0.2f
const val LEAN_MAX = 0.14f
const val LIFT_MAX = 1.1f

class MascotMotion(
    private val view: SlimeView,
    private val handler: Handler,
    private val density: Float,
    private val eyesOpen: Bitmap,
    private val eyesClosed: Bitmap,
    /** Move a janela da gotinha na horizontal (px, coordenada da janela). */
    private val setWindowX: (Int) -> Unit,
    private val enabled: () -> Boolean,
    /** Escala de duração de animações do sistema (1 = normal). */
    private val timeScale: () -> Float,
    private val random: Random = Random.Default,
) {
    // Forma
    private val squash = Spring(0f, 300f, 0.35f)
    private val lean = Spring(0f, 180f, 0.32f)
    private val lift = Spring(0f, 400f, 0.8f)
    private val hop = Spring(0f, 260f, 0.45f)
    // Janela
    private val winX = Spring(0f, 220f, 0.75f)
    private var winActive = false
    /** Parede (x da janela) contra a qual a gotinha amassa ao chegar; null = sem parede. */
    private var wall: Int? = null
    private var wallSide = 1f
    private var onWindowArrive: (() -> Unit)? = null

    private var breathUntil = 0L
    private var breathStart = 0L
    private var running = false
    private var looping = false
    private var lastFrame = 0L
    private var lastInteraction = SystemClock.uptimeMillis()
    private val script = mutableListOf<Runnable>()

    private val breathTask = Runnable { breathe() }
    private val blinkTask = Runnable { blink() }
    private val reopenEyes = Runnable { view.setImage(eyesOpen) }

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(now: Long) {
            if (!looping) return
            val scale = timeScale().coerceAtLeast(0.01f)
            val dt = if (lastFrame == 0L) 1f / 60f else ((now - lastFrame) / 1e9f).coerceIn(0f, 1f / 30f)
            lastFrame = now
            tick(dt / scale)
            if (settled()) { looping = false; lastFrame = 0L } else Choreographer.getInstance().postFrameCallback(this)
        }
    }

    private fun px(dp: Float) = dp * density
    private fun calm() = SystemClock.uptimeMillis() - lastInteraction > MascotTiming.CALM_AFTER_MS
    private fun visible() = view.isAttachedToWindow && view.visibility == View.VISIBLE && view.windowVisibility == View.VISIBLE
    private fun animate() = enabled() && timeScale() > 0f

    private fun settled() = !winActive && squash.atRest(0.002f) && lean.atRest(0.002f) && lift.atRest(0.004f) &&
        hop.atRest(0.2f) && SystemClock.uptimeMillis() > breathUntil

    private fun kick() {
        if (!visible()) return
        if (!looping) { looping = true; lastFrame = 0L; Choreographer.getInstance().postFrameCallback(frame) }
    }

    private fun tick(dt: Float) {
        squash.step(dt); lean.step(dt); lift.step(dt); hop.step(dt)
        var breath = 0f
        val now = SystemClock.uptimeMillis()
        if (now < breathUntil) {
            // Respiração: sobe e desce devagar (seno), somada ao que as molas estiverem fazendo.
            val t = (now - breathStart).toFloat() / (breathUntil - breathStart)
            breath = -0.022f * kotlin.math.sin(Math.PI.toFloat() * t)
        }
        if (winActive) {
            winX.step(dt)
            val w = wall
            if (w != null && (winX.value - w) * wallSide > 0f) {
                // Encostou na borda: a velocidade vira amassado e o corpo balança para dentro.
                val impact = (abs(winX.velocity) / px(1400f)).coerceAtMost(1f)
                squash.velocity -= 1.6f * impact
                lean.velocity -= wallSide * 1.1f * impact
                winX.snap(w.toFloat())
            }
            setWindowX(winX.value.toInt())
            if (winX.atRest(0.5f)) { winActive = false; setWindowX(winX.target.toInt()); onWindowArrive?.also { onWindowArrive = null }?.invoke() }
        }
        view.pose = SlimePose(
            squash = (squash.value + breath).coerceIn(SQUASH_MIN, SQUASH_MAX),
            lean = lean.value.coerceIn(-LEAN_MAX, LEAN_MAX),
            lift = lift.value.coerceIn(0f, LIFT_MAX),
            dy = hop.value,
        )
    }

    private fun later(ms: Long, block: () -> Unit) {
        val r = Runnable { block() }
        script += r
        handler.postDelayed(r, (ms * timeScale().coerceAtLeast(0.01f)).toLong())
    }

    private fun clearScript() { script.forEach { handler.removeCallbacks(it) }; script.clear() }

    private fun rest() {
        for (s in listOf(squash, lean, lift, hop)) s.snap(0f)
        breathUntil = 0L
        view.pose = SlimePose()
    }

    // ---- ciclo de vida ------------------------------------------------------------------------

    /** Retoma o repouso (respiração e piscadas agendadas). */
    fun start() {
        if (running) return
        running = true
        scheduleBreath(); scheduleBlink()
    }

    /** Para tudo: sem quadros, sem tarefas pendentes, forma de repouso e janela no destino. */
    fun stop() {
        running = false; looping = false; lastFrame = 0L
        Choreographer.getInstance().removeFrameCallback(frame)
        handler.removeCallbacks(breathTask); handler.removeCallbacks(blinkTask); handler.removeCallbacks(reopenEyes)
        clearScript()
        if (winActive) { winActive = false; setWindowX(winX.target.toInt()) }
        onWindowArrive = null
        view.setImage(eyesOpen)
        rest()
    }

    /** Interrompe só o deslocamento da janela (novo toque, giro da tela). */
    fun cancelWindow() { winActive = false; onWindowArrive = null; wall = null }

    // ---- repouso -----------------------------------------------------------------------------

    private fun scheduleBreath() {
        handler.removeCallbacks(breathTask)
        if (running) handler.postDelayed(breathTask, MascotTiming.nextBreathDelay(random, calm()))
    }

    private fun scheduleBlink() {
        handler.removeCallbacks(blinkTask)
        if (running) handler.postDelayed(blinkTask, MascotTiming.nextBlinkDelay(random, calm()))
    }

    private fun breathe() {
        if (!running) return
        if (visible() && animate() && !winActive && lift.target == 0f &&
            MascotTiming.breathes(SystemClock.uptimeMillis() - lastInteraction)) {
            breathStart = SystemClock.uptimeMillis()
            breathUntil = breathStart + (MascotTiming.BREATH_MS * timeScale()).toLong()
            kick()
        }
        scheduleBreath()
    }

    private fun blink(double: Boolean = random.nextInt(5) == 0) {
        if (!running) return
        // Não pisca durante o arraste nem enquanto desliza até a borda.
        if (visible() && animate() && lift.target == 0f && !winActive) {
            closeEyes()
            if (double) handler.postDelayed({ if (running) closeEyes() }, MascotTiming.BLINK_MS + 170)
        }
        scheduleBlink()
    }

    /**
     * Piscada: olhos fechados do mesmo desenho por um instante. Em repouso é só a troca de imagem (dois quadros);
     * com [bump], as bochechas sobem um tiquinho (fim da entrada).
     */
    private fun closeEyes(bump: Boolean = false) {
        view.setImage(eyesClosed)
        handler.removeCallbacks(reopenEyes)
        handler.postDelayed(reopenEyes, MascotTiming.BLINK_MS)
        if (bump) { squash.velocity += 0.25f; kick() }
    }

    private fun touched() { lastInteraction = SystemClock.uptimeMillis(); breathUntil = 0L }

    // ---- entrada e saída ---------------------------------------------------------------------

    /**
     * Entrada pela borda em [restX] (janela). [fromRight]: a borda é a direita. [width]: largura da janela.
     * [short]: versão mais curta, para quando a gotinha volta depois de uma captura.
     */
    fun enter(restX: Int, fromRight: Boolean, width: Int, short: Boolean = false) {
        touched(); clearScript()
        view.setImage(eyesOpen)
        if (!animate()) { cancelWindow(); rest(); setWindowX(restX); view.alpha = 1f; return }
        val out = if (fromRight) 1f else -1f
        view.alpha = 1f
        // 1. Espia: só a pontinha aparece, ainda esticada e inclinada para dentro da borda.
        winX.snap(restX + out * width * (if (short) 0.55f else 0.5f)); setWindowX(winX.value.toInt())
        squash.snap(-0.1f); lean.snap(0.07f * out); lift.snap(0f); hop.snap(0f)
        wall = null
        kick()
        val peek = if (short) 70L else 190L
        // 2. Prepara: encolhe e se inclina para trás, tomando impulso.
        later(if (short) 0L else 60L) {
            squash.set(420f, 0.7f); squash.target = 0.11f
            lean.set(260f, 0.6f); lean.target = 0.1f * out
            kick()
        }
        // 3. Sai: dispara para o lugar, esticada, com o corpo atrasado em relação ao movimento; passa um pouco do
        //    lugar e volta (mola com pouco amortecimento).
        later(peek) {
            squash.set(260f, 0.3f); squash.target = 0f; squash.velocity = -2.4f
            lean.set(150f, 0.28f); lean.target = 0f; lean.velocity = 1.45f * out
            winX.set(170f, 0.52f); winX.target = restX.toFloat(); winX.velocity = -out * px(900f)
            winActive = true; wall = null
            onWindowArrive = null
            kick()
        }
        // 4. Freia: ao atingir o ponto mais distante, achata e balança até assentar; termina com uma piscada.
        later(peek + 170L) { squash.velocity += 2.3f; hop.velocity -= px(40f); kick() }
        later(peek + (if (short) 520L else 760L)) { if (running) closeEyes(bump = true) }
    }

    /** Saída pela borda (desativar): se encolhe, toma impulso e escorrega para fora. */
    fun exit(restX: Int, toRight: Boolean, width: Int, done: () -> Unit) {
        clearScript()
        if (!animate() || !visible()) { done(); return }
        val out = if (toRight) 1f else -1f
        squash.set(420f, 0.7f); squash.target = 0.12f
        lean.set(260f, 0.6f); lean.target = -0.08f * out
        kick()
        later(130L) {
            squash.set(260f, 0.5f); squash.target = -0.1f
            lean.target = -0.06f * out
            winX.snap(restX.toFloat()); winX.set(150f, 1f); winX.target = restX + out * width * 1.05f; winX.velocity = out * px(500f)
            wall = null; winActive = true
            onWindowArrive = done
            kick()
        }
        // Garante o fim mesmo se a mola demorar a assentar fora da tela.
        later(700L) { if (onWindowArrive != null) { val d = onWindowArrive; onWindowArrive = null; winActive = false; d?.invoke() } }
    }

    // ---- toque, arraste e encaixe ------------------------------------------------------------

    /** Dedo encostou: cede na hora (sem esperar mola). */
    fun press() {
        touched(); clearScript(); cancelWindow()
        if (!animate()) return
        squash.set(900f, 0.85f); squash.target = 0.085f; squash.velocity += 0.9f
        lean.set(300f, 0.6f); lean.target = 0f
        kick()
    }

    /** Soltou sem arrastar: volta com um "boing" curto. */
    fun release() {
        if (!animate()) { rest(); return }
        squash.set(320f, 0.28f); squash.target = 0f; squash.velocity -= 0.8f
        lift.target = 0f
        kick()
    }

    /** Começou a arrastar: fica erguida. */
    fun dragStart() {
        touched()
        if (!animate()) return
        squash.set(360f, 0.55f); squash.target = 0f
        lift.set(420f, 0.7f); lift.target = 1f
        kick()
    }

    /** Durante o arraste, a cada movimento: [vx], [vy] em px/s. O corpo atrasa e estica no sentido do gesto. */
    fun dragMove(vx: Float, vy: Float) {
        if (!animate()) return
        val ref = px(2600f)
        lean.set(220f, 0.45f)
        lean.target = (-vx / ref * 0.13f).coerceIn(-0.13f, 0.13f)
        val speed = sqrt(vx * vx + vy * vy)
        squash.target = (-(speed / ref) * 0.08f).coerceIn(-0.09f, 0f) + (vy / ref * 0.03f).coerceIn(-0.03f, 0.03f)
        kick()
    }

    /**
     * Soltou depois de arrastar: desliza de [fromX] até a borda [toX] com a velocidade do gesto [vx] e amassa
     * ao encostar. Sem animação, vai direto.
     */
    fun dragEnd(fromX: Int, toX: Int, vx: Float) {
        lift.target = 0f
        if (!animate()) { rest(); setWindowX(toX); return }
        squash.set(300f, 0.35f); squash.target = 0f
        lean.set(180f, 0.32f); lean.target = 0f
        val side = if (toX >= fromX) 1f else -1f
        winX.snap(fromX.toFloat())
        // Velocidade mínima em direção à borda, para sempre haver um encaixe visível, e máxima para não "voar".
        val toward = (vx * side).coerceIn(px(500f), px(3200f))
        winX.velocity = toward * side
        winX.set(160f, 0.9f); winX.target = toX.toFloat()
        wall = toX; wallSide = side
        winActive = true; onWindowArrive = { wall = null }
        kick()
    }

    // ---- painel e ações ----------------------------------------------------------------------

    /** Painel aberto do lado [towardLeft]: a gotinha se estica um pouco e se inclina para ele enquanto estiver aberto. */
    fun panelOpened(towardLeft: Boolean) {
        touched()
        if (!animate()) return
        squash.set(240f, 0.42f); squash.target = -0.035f; squash.velocity -= 0.6f
        lean.set(160f, 0.45f); lean.target = if (towardLeft) -0.045f else 0.045f
        kick()
    }

    /** Painel fechado: solta a inclinação e balança até assentar. */
    fun panelClosed() {
        if (!animate()) { rest(); return }
        squash.set(260f, 0.3f); squash.target = 0f
        lean.set(170f, 0.28f); lean.target = 0f
        kick()
    }

    /** Uma ação foi pedida (ler a tela, abrir a análise): acena com o corpo ("entendi") e assenta. */
    fun acknowledged() {
        touched()
        if (!animate()) { rest(); return }
        squash.set(300f, 0.32f); squash.target = 0f; squash.velocity += 1.4f
        lean.set(170f, 0.3f); lean.target = 0f
        kick()
    }
}
