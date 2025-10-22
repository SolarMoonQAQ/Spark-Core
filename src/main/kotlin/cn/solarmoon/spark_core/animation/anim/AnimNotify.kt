package cn.solarmoon.spark_core.animation.anim

import kotlin.math.abs

sealed class AnimNotify(val id: String) {

    abstract fun check(anim: AnimInstance)

    class Point(id: String, val time: Float) : AnimNotify(id) {
        private val handlers = mutableListOf<Point.(AnimInstance) -> Unit>()
        private var lastTime: Float = -1f

        fun onEnter(handler: Point.(AnimInstance) -> Unit) {
            handlers.add(handler)
        }

        override fun check(anim: AnimInstance) {
            val t = anim.typedTime.toFloat()

            if (time == 0f && lastTime < 0f && t == 0f) {
                handlers.forEach { it(this, anim) }
            }

            if (lastTime >= 0f) {
                if (lastTime < time && t >= time) {
                    handlers.forEach { it(this, anim) }
                }
            }
            lastTime = t
        }
    }

    class State(id: String, val start: Float, val end: Float) : AnimNotify(id) {
        private val beginHandlers = mutableListOf<State.(AnimInstance) -> Unit>()
        private val tickHandlers  = mutableListOf<State.(AnimInstance) -> Unit>()
        private val endHandlers   = mutableListOf<State.(AnimInstance) -> Unit>()
        internal var entered = false
        internal var exited = false
        fun onEnter(handler: State.(AnimInstance) -> Unit) {
            beginHandlers.add(handler)
        }
        fun onTick(handler: State.(AnimInstance) -> Unit) {
            tickHandlers.add(handler)
        }
        fun onExit(handler: State.(AnimInstance) -> Unit) {
            endHandlers.add(handler)
        }

        override fun check(anim: AnimInstance) {
            val t = anim.typedTime
            val inRange = t in start..end

            if (inRange) {
                if (!entered) {
                    entered = true
                    exited = false
                    beginHandlers.forEach { it(this, anim) }
                }
                tickHandlers.forEach { it(this, anim) }
            } else {
                if (entered && !exited) {
                    exited = true
                    endHandlers.forEach { it(this, anim) }
                }
                // 循环动画重置
                if (t < start) {
                    entered = false
                    exited = false
                }
            }
        }
    }

}
