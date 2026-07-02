package cn.solarmoon.spark_core.state_machine.graph

/**
 * 类型化状态变量容器 —— 带默认值的键值存储。
 *
 * 用于替代纯布尔型 GameplayTagContainer，承载速度、方向等数值型状态。
 * 与 [StateVariableKey] 配合使用，按 key 的类型信息防止误用。
 *
 * 线程安全：读写全部在物理线程执行，内部使用普通 [MutableMap]，无需同步。
 */
class StateVariableContainer {

    /** 内部存储：key → 已写入的值 */
    private val data = mutableMapOf<StateVariableKey<*>, Any>()

    /**
     * 获取变量值，未写入时返回 key 中定义的默认值。
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> get(key: StateVariableKey<T>): T =
        if (data.containsKey(key)) data[key] as T else key.defaultValue

    /**
     * 写入变量值。
     */
    fun <T : Any> set(key: StateVariableKey<T>, value: T) {
        @Suppress("UNCHECKED_CAST")
        (data as MutableMap<StateVariableKey<*>, Any?>)[key] = value
    }

    /**
     * 判断是否已显式写入值（用于区分"未写入"与"显式写入默认值"）。
     */
    fun contains(key: StateVariableKey<*>): Boolean = data.containsKey(key)

    /**
     * 移除写入值，使其回退到默认值。
     */
    fun <T : Any> remove(key: StateVariableKey<T>) {
        data.remove(key)
    }

    /**
     * 清空所有写入，全部回退到默认值。
     */
    fun clear() {
        data.clear()
    }

    /**
     * 帧首快照覆盖 —— 用 [source] 的全部数据替换当前存储。
     */
    fun replaceAll(source: StateVariableContainer) {
        data.clear()
        data.putAll(source.data)
    }
}
