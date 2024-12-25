package cn.solarmoon.spark_core.util

class CycleIndex(private val max: Int) {
    private var index = 0

    fun get(): Int {
        return index
    }

    fun increment(step: Int = 1) {
        index = (index + step) % max
    }

    fun decrement(step: Int = 1) {
        index = (index - step + max) % max
    }

    fun set(index: Int) {
        this.index = (index % max + max) % max
    }
}
