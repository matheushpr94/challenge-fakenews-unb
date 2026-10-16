package com.example.lumeocrtest.capture

/** Diz se a tela principal do Lume está na frente. A captura pela gotinha usa isso para nunca fotografar o próprio Lume. */
object AppVisibility {
    @Volatile var mainResumed: Boolean = false
}
