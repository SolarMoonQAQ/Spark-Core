package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.SkillInstance
import com.mojang.serialization.MapCodec
import net.minecraft.resources.ResourceLocation
import java.util.function.Function
import kotlin.reflect.KClass

abstract class SkillComponent(
    children: List<SkillComponent>
) {

    val id get() = SparkRegistries.SKILL_COMPONENT_CODEC.getId(codec)

    val registryKey: ResourceLocation get() = SparkRegistries.SKILL_COMPONENT_CODEC.getKey(codec) ?: throw NullPointerException("技能组件尚未注册")

    var parent: SkillComponent? = null

    val children: List<SkillComponent> = children.map { it.copy() }

    val context: MutableList<Any> = mutableListOf()

    abstract val codec: MapCodec<out SkillComponent>

    abstract fun copy(): SkillComponent

    protected abstract fun onActive(skill: SkillInstance): Boolean

    protected abstract fun onUpdate(skill: SkillInstance): Boolean

    protected abstract fun onEnd(skill: SkillInstance): Boolean

    fun active(skill: SkillInstance) {
        if (onActive(skill) && children.isNotEmpty()) {
            children.forEach {
                it.parent = this
                it.active(skill)
            }
        }
    }

    fun update(skill: SkillInstance) {
        if (onUpdate(skill) && children.isNotEmpty()) {
            children.forEach { it.onUpdate(skill) }
        }
    }

    fun end(skill: SkillInstance) {
        if (onEnd(skill) && children.isNotEmpty()) {
            children.forEach { it.onEnd(skill) }
        }
    }

    fun addContext(content: Any) {
        context.add(content)
    }

    protected inline fun <reified T: Any> registerContext(type: KClass<T>, ordinal: Int = 0) =
        parent?.context?.filter { it::class == type }?.getOrNull(ordinal) as? T ?: throw NullPointerException("组件 $registryKey 的父组件必须包含一个 ${type.simpleName} 上下文参数")

    companion object {
        val CODEC = SparkRegistries.SKILL_COMPONENT_CODEC.byNameCodec()
            .dispatch(
                SkillComponent::codec,
                Function.identity()
            )
    }

}