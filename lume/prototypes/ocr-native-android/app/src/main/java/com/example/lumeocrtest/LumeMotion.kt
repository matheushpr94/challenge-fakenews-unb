package com.example.lumeocrtest

import androidx.compose.animation.*
import androidx.compose.runtime.*

/** App preference complements Android's animation duration scale. */
val LocalLumeMotion = staticCompositionLocalOf { true }

@Composable
fun LumeVisibility(visible: Boolean, content: @Composable AnimatedVisibilityScope.() -> Unit) {
    val motion = LocalLumeMotion.current
    AnimatedVisibility(visible, enter=if(motion) fadeIn()+expandVertically() else EnterTransition.None,
        exit=if(motion) fadeOut()+shrinkVertically() else ExitTransition.None, content=content)
}
