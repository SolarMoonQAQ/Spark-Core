package cn.solarmoon.spark_core.gas

enum class InstancingPolicy {
//    /** 不实例化，所有逻辑在 Ability 类本身执行 */
//    NON_INSTANCED,

    /** 每个技能持有者一个实例，所有激活复用同一个 Ability */
    INSTANCED_PER_ACTOR,

    /** 每次激活新建一个实例，可以并行多个 */
    INSTANCED_PER_EXECUTION
}
