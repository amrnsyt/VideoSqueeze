package com.amrnsyt.videosqueeze.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith

/** Tab content slides a little in the direction of travel while fading. */
fun tabTransition(): AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
    val dir = if (targetState > initialState) 1 else -1
    (fadeIn(tween(220)) + slideInHorizontally(tween(260)) { it * dir / 10 }) togetherWith
        (fadeOut(tween(120)) + slideOutHorizontally(tween(200)) { -it * dir / 10 })
}
