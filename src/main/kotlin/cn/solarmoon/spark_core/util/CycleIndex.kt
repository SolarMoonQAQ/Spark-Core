package cn.solarmoon.spark_core.util

class CycleIndex(private val max: Int, defaultValue: Int = 0) {

    private var index = defaultValue

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
        this.index = index.coerceIn(0, max - 1)
    }

}
