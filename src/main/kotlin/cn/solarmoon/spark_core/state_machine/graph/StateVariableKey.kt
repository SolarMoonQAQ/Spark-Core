package cn.solarmoon.spark_core.state_machine.graph

import net.minecraft.resources.ResourceLocation
import kotlin.reflect.KClass

/**
 * 状态变量键 —— 类型化、带默认值的状态变量标识。
 *
 * 参考 BallisticsFramework `BFDamageExtensions` 模式——每个 key 带默认值，
 * `contains()` 区分"未写入"与"显式写入默认值"。
 * 附带 [type] 运行时类型信息，防止泛型擦除导致同 id 不同类型 key 误用。
 *
 * @param T 变量值的类型
 * @param id 唯一标识（ResourceLocation 格式）
 * @param type 运行时类型信息（用于防止泛型擦除导致的键冲突）
 * @param defaultValue 未写入时的默认返回值
 */
data class StateVariableKey<T : Any>(
    val id: ResourceLocation,
    val type: KClass<T>,
    val defaultValue: T
)
