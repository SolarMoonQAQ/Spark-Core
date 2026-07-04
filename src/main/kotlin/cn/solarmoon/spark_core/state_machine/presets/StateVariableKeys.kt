package cn.solarmoon.spark_core.state_machine.presets

import cn.solarmoon.spark_core.state_machine.graph.StateVariableKey
import net.minecraft.resources.ResourceLocation

/**
 * 预定义状态变量键 —— Spark-Core 内置的标准变量。
 *
 * 所有 key 遵循 `spark_core:` 命名空间约定。
 * 下游模组可在各自域下扩展（如 `arms_core:xxx`、`machine_max:xxx`）。
 */
object StateVariableKeys {

    /** 是否在地面 */
    @JvmField val ON_GROUND: StateVariableKey<Boolean> = key("spark_core:on_ground", false)

    /** 水平速度（m/s） */
    @JvmField val SPEED: StateVariableKey<Float> = key("spark_core:speed", 0f)

    /** 垂直速度（m/s，正=上升） */
    @JvmField val VERTICAL_SPEED: StateVariableKey<Float> = key("spark_core:vertical_speed", 0f)

    /** 是否在冲刺 */
    @JvmField val IS_SPRINTING: StateVariableKey<Boolean> = key("spark_core:is_sprinting", false)

    /** 是否在水中 */
    @JvmField val IS_SWIMMING: StateVariableKey<Boolean> = key("spark_core:is_swimming", false)

    /** 是否已死亡 */
    @JvmField val IS_DEAD: StateVariableKey<Boolean> = key("spark_core:is_dead", false)

    /** 前进输入强度（-1 ~ 1） */
    @JvmField val INPUT_FORWARD: StateVariableKey<Float> = key("spark_core:input_forward", 0f)

    /** 侧移输入强度（-1 ~ 1） */
    @JvmField val INPUT_STRAFE: StateVariableKey<Float> = key("spark_core:input_strafe", 0f)

    /** 当前状态所有动画是否均已完成 */
    @JvmField val ALL_ANIMATIONS_FINISHED: StateVariableKey<Boolean> = key("spark_core:all_animations_finished", true)

    /** 当前状态是否有任意动画已完成 */
    @JvmField val ANY_ANIMATION_FINISHED: StateVariableKey<Boolean> = key("spark_core:any_animation_finished", true)

    /**
     * 便捷构造工厂。
     * 利用内联函数具现化类型参数，直接获取准确的 [kotlin.reflect.KClass]。
     */
    private inline fun <reified T : Any> key(name: String, default: T): StateVariableKey<T> =
        StateVariableKey(ResourceLocation.parse(name), T::class, default)
}
