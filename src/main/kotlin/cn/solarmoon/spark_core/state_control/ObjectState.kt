package cn.solarmoon.spark_core.state_control

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillType
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceLocation
import net.minecraft.tags.TagKey
import net.minecraft.world.entity.Entity

class ObjectState<T>(
    val name: String,
    val registryKey: ResourceLocation,
    private val condition: (T) -> Boolean
) {

    val resourceKey get() = SparkRegistries.STATE.getResourceKey(this).get()

    val builtInRegistryHolder get() = SparkRegistries.STATE.getHolder(resourceKey).get()

    /**
     * **维持**该状态所需的**基本条件**
     *
     * 如能否维持奔跑状态直接返回[net.minecraft.world.entity.Entity.isSprinting]即可
     *
     * *注意：MC对于很多状态属性是双端不一致的，务必检查双端同步*
     */
    fun getCondition(ob: T): Boolean {
        return condition.invoke(ob)
    }

    /**
     * @return 是否满足条件
     */
    fun setIfMetCondition(ob: T, machine: StateMachine<T>): Boolean {
        if (getCondition(ob)) {
            machine.setState(this)
            return true
        }
        return false
    }

    fun `is`(tag: TagKey<ObjectState<*>>) = builtInRegistryHolder.`is`(tag)

    override fun equals(other: Any?): Boolean {
        return (other as? ObjectState<T>)?.registryKey == this.registryKey
    }

    override fun hashCode(): Int {
        return registryKey.hashCode()
    }

}