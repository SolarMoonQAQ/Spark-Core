package cn.solarmoon.spark_core.animation.anim

sealed class AnimEvent {

    /**
     * 动画将要被设为当前动画时触发
     */
    object Start: AnimEvent()

    /**
     * 动画被cancel指令打断时触发（不是由于其它动画进入，而是仅对该动画本身的切断）
     */
    object Interrupted : AnimEvent()

    /**
     * 动画自然结束时触发
     */
    object Completed : AnimEvent()

    /**
     * 动画无论因何种原因被设为删除时触发（自然结束、被其它动画打断、手动打断）
     */
    object End : AnimEvent()

    /**
     * 动画生命周期tick时触发
     */
    object Tick : AnimEvent()

}