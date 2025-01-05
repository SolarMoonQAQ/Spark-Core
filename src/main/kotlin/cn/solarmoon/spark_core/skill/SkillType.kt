package cn.solarmoon.spark_core.skill

import net.minecraft.resources.ResourceKey
import net.minecraft.resources.ResourceLocation

/**
 * ### 技能类型
 * 技能类型将实例化的技能与抽象的技能功能分离，从而能对实现了同种指令的技能进行统一的调配
 *
 * 同时技能也需要通过此类进行包装后注册，注册后可以使用原版的诸如tag等功能（注册可用[cn.solarmoon.spark_core.entry_builder.ObjectRegister.skillType]）
 */
class SkillType<T, S: Skill<T>>(
    val registryKey: ResourceLocation,
    private val skillInstance: (T, SkillType<T, S>) -> S
) {

    fun create(holder: T): S {
        return skillInstance.invoke(holder, this)
    }

    override fun equals(other: Any?): Boolean {
        return (other as? SkillType<*, *>)?.registryKey == this.registryKey
    }

    override fun hashCode(): Int {
        return registryKey.hashCode()
    }

}