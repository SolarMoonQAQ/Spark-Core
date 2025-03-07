package cn.solarmoon.spark_core.util

import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class BlackBoard {

    val storage = ConcurrentHashMap<Key<*>, Any>()

    fun <T> write(key: Key<T>, value: T) {
        storage[key] = value as Any
    }

    inline fun <reified T> read(key: Key<T>): T? = storage[key] as? T

    inline fun <reified T> require(key: Key<T>, applicant: Any) = read(key) ?: throw NullPointerException("${applicant.javaClass.simpleName} 需要 ${key.name}(${key.type.simpleName}) 才能继续操作")

}