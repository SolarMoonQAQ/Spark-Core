package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.skill.SkillInstance
import com.mojang.serialization.MapCodec
import net.minecraft.nbt.CompoundTag
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Function

abstract class SkillComponent {

    lateinit var skill: SkillInstance

    var ordinal = 0

    val holder get() = skill.holder

    val level get() = skill.level

    open fun init() {}

    open fun onActive() {}

    open fun onUpdate() {}

    open fun onEnd() {}

    open fun sync(host: SkillHost, data: CompoundTag, context: IPayloadContext) {}

    abstract fun copy(): SkillComponent

    abstract val codec: MapCodec<out SkillComponent>

    companion object {
        val CODEC = SparkRegistries.SKILL_COMPONENT_CODEC.byNameCodec()
            .dispatch(
                SkillComponent::codec,
                Function.identity()
            )
    }

}