package cn.solarmoon.spark_core.skill.node

import java.util.ArrayDeque
import java.util.Deque

class BlackBoard(
    val global: MutableMap<String, Any> = mutableMapOf(),
    val nodeStack: Deque<MutableMap<String, Any>> = ArrayDeque()
) {

    val current: MutableMap<String, Any> get() = nodeStack.peek() ?: global

    fun pushLayer() = nodeStack.push(mutableMapOf())

    fun popLayer() = nodeStack.pop()

    inline fun <reified T> require(key: String): T = get(key) ?: throw NullPointerException("行为节点需要 $key(${T::class.simpleName}) 参量才能正常运行")

    operator fun <T> get(key: String): T? = (current[key] ?: global[key]) as? T

    operator fun set(key: String, value: Any) { current[key] = value }

}