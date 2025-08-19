package cn.solarmoon.spark_core.animation.anim.play

open class KeyframeEvent {
    object Enter: KeyframeEvent()
    class Inside(val time: Double): KeyframeEvent()
    object Exit: KeyframeEvent()
}