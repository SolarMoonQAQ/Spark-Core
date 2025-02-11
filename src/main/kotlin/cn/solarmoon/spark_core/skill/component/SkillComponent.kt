package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.SkillInstance
import com.mojang.serialization.MapCodec
import net.minecraft.resources.ResourceLocation
import java.util.function.Function

abstract class SkillComponent {

    val id get() = SparkRegistries.SKILL_COMPONENT_CODEC.getId(codec)

    val registryKey: ResourceLocation get() = SparkRegistries.SKILL_COMPONENT_CODEC.getKey(codec) ?: throw NullPointerException("技能组件尚未注册")

    var parent: SkillComponent? = null

    lateinit var skill: SkillInstance

    val context get() = skill.context

    val level get() = skill.level

    private val children = mutableSetOf<SkillComponent>()

    abstract val codec: MapCodec<out SkillComponent>

    abstract fun copy(): SkillComponent

    protected abstract fun onActive()

    protected abstract fun onUpdate()

    protected abstract fun onEnd()

    fun active(skill: SkillInstance) {
        this.skill = skill
        onActive()
    }

    fun update() {
        onUpdate()
        children.forEach { it.update() }
    }

    fun end() {
        onEnd()
        children.forEach { it.end() }
    }

    fun setCustomActive(components: List<SkillComponent>) {
        children.addAll(components)
        components.forEach { it.active(skill) }
    }

    fun addOrReplaceContext(key: String, content: Any) {
        try {
            skill.context[key] = content
        } catch (e: Exception) {
            throw NullPointerException("必须在技能启动之后才能添加上下文参数")
        }
    }

    inline fun <reified T> query(path: String): T? = skill.context[path] as? T

    inline fun <reified T> requireQuery(path: String): T = query(path) ?: throw IllegalStateException("组件 $registryKey 所属技能必须包含一个 [$path](${T::class.simpleName}) 上下文参数")

    companion object {
        val CODEC = SparkRegistries.SKILL_COMPONENT_CODEC.byNameCodec()
            .dispatch(
                SkillComponent::codec,
                Function.identity()
            )
    }

}