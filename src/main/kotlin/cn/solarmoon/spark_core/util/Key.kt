package cn.solarmoon.spark_core.util

class Key<T>(val name: String? = null) {
    companion object {
        inline fun <reified T> create(name: String) = Key<T>(name)
    }
}