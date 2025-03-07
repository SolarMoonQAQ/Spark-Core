package cn.solarmoon.spark_core.animation.vanilla

interface ITransformModel {

    fun shouldTransform(): Boolean

    fun setShouldTransform(transform: Boolean)

}