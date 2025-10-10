package cn.solarmoon.spark_core.util

import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

open class BlackBoard {

    val storage = ConcurrentHashMap<Key<*>, Any>()

    val isEmpty get() = storage.isEmpty()

    fun <T> write(key: Key<T>, value: T) {
        storage[key] = value as Any
    }

    fun write(storage: Map<Key<*>, Any>) {
        this.storage.putAll(storage)
    }

    fun clear() {
        storage.clear()
    }

    inline fun <reified T> read(key: Key<T>): T? = storage[key] as? T

    inline fun <reified T> require(key: Key<T>, applicant: Any) = read(key) ?: throw NullPointerException("${applicant.javaClass.simpleName} 需要 ${key.name}(${T::class.simpleName}) 才能继续操作")

}