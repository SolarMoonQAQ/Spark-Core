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

    lateinit var skill: SkillInstance

    val children: List<SkillComponent> = children.map { it.copy() }

    abstract val codec: MapCodec<out SkillComponent>

    abstract fun copy(): SkillComponent

    protected abstract fun onActive(): Boolean

    protected abstract fun onUpdate(): Boolean

    protected abstract fun onEnd(): Boolean

    fun active(skill: SkillInstance) {
        this.skill = skill
        if (onActive() && children.isNotEmpty()) {
            children.forEach {
                it.parent = this
                it.active(skill)
            }
        }
    }

    fun update(skill: SkillInstance) {
        if (onUpdate() && children.isNotEmpty()) {
            children.forEach { it.onUpdate() }
        }
    }

    fun end(skill: SkillInstance) {
        if (onEnd() && children.isNotEmpty()) {
            children.forEach { it.onEnd() }
        }
    }

    fun addContext(content: Any) {
        try {
            skill.context.add(content)
        } catch (e: Exception) {
            throw NullPointerException("必须在技能启动之后才能添加上下文参数")
        }
    }

    protected inline fun <reified T: Any> registerContext(type: KClass<T>, ordinal: Int = 0) =
        skill.context.filter { type.isInstance(it) }.getOrNull(ordinal) as? T ?: throw NullPointerException("组件 $registryKey 所属技能必须包含一个 ${type.simpleName} 上下文参数")

    companion object {
        val CODEC = SparkRegistries.SKILL_COMPONENT_CODEC.byNameCodec()
            .dispatch(
                SkillComponent::codec,
                Function.identity()
            )
    }

}