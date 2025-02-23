package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.component.body_binder.RigidBodyBinder
import com.mojang.serialization.JsonOps
import com.mojang.serialization.MapCodec
import net.minecraft.nbt.CompoundTag
import net.neoforged.bus.api.Event
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Function

abstract class SkillComponent {

    lateinit var skill: Skill
    var ordinal = 0
        private set

    fun attach(skill: Skill) {
        this.skill = skill
        if (skill.components.add(this)) {
            ordinal = skill.components.size - 1
        }
        onAttach()
    }

    fun tick() {
        onTick()
    }

    fun detach() {
        onDetach()
    }

    fun handleEvent(event: Event) {
        onEvent(event)
    }

    protected open fun onAttach() {}

    protected open fun onTick() {}

    protected open fun onDetach() {}

    protected open fun onEvent(event: Event) {}

    open fun sync(data: CompoundTag, context: IPayloadContext) {}

    abstract val codec: MapCodec<out SkillComponent>

    companion object {
        val CODEC = SparkRegistries.SKILL_COMPONENT_CODEC.byNameCodec()
            .dispatch(
                SkillComponent::codec,
                Function.identity()
            )
    }

}