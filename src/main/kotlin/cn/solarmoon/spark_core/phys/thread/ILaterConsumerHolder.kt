package cn.solarmoon.spark_core.phys.thread

interface ILaterConsumerHolder {

    val consumers: ArrayDeque<() -> Unit>

}