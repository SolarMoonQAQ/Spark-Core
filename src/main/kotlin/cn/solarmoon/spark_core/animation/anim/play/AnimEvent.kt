package cn.solarmoon.spark_core.animation.anim.play

sealed class AnimEvent {

    /**
     * 动画将要被设为当前动画时触发
     */
    data class SwitchIn(val previous: AnimInstance?) : AnimEvent()

    /**
     * 下一个动画将要取代当前动画时触发
     */
    data class SwitchOut(val next: AnimInstance?) : AnimEvent()

    /**
     * 动画被cancel指令打断时触发
     */
    object Interrupted : AnimEvent()

    /**
     * 动画自然结束时触发
     */
    object Completed : AnimEvent()

    /**
     * 动画无论因何种原因被设为删除时触发（自然结束、被其它动画打断、手动打断）
     */
    data class End(val by: AnimEvent) : AnimEvent()

    /**
     * 动画生命周期tick时触发
     */
    object Tick : AnimEvent()

}