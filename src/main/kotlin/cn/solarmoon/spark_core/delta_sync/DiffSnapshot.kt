package cn.solarmoon.spark_core.delta_sync

data class DiffSnapshot(val values: MutableMap<Long, Any?>) {
    companion object {
        fun empty() = DiffSnapshot(mutableMapOf())
    }
}