package com.example.lumeocrtest.research

/**
 * Garante que só a ação mais recente (pesquisa, esclarecimento ou nova imagem) pode mostrar resultado.
 * Cada ação recebe um número; qualquer ação nova ou `invalidate()` torna os números antigos obsoletos.
 * Respostas atrasadas de pesquisas anteriores são descartadas mesmo que a rede não as cancele a tempo.
 */
class RequestGate {
    private var generation = 0L

    @Synchronized
    fun next(): Long = ++generation

    @Synchronized
    fun isCurrent(ticket: Long): Boolean = ticket == generation

    @Synchronized
    fun invalidate() {
        generation++
    }

    /** Executa `apply` apenas se `ticket` ainda for a ação atual. */
    inline fun <T> deliver(ticket: Long, value: T, apply: (T) -> Unit): Boolean {
        if (!isCurrent(ticket)) return false
        apply(value)
        return true
    }
}
