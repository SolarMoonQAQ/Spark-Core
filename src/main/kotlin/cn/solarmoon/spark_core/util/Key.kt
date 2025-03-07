package cn.solarmoon.spark_core.util

class Key<T>(val name: String? = null, val type: Class<T>) {
    companion object {
        inline fun <reified T> create(name: String) = Key(name, T::class.java)
    }
}