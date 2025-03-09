package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.registry.common.SparkRegistries
import cn.solarmoon.spark_core.skill.Skill
import com.mojang.serialization.JsonOps
import com.mojang.serialization.MapCodec
import net.minecraft.nbt.CompoundTag
import net.neoforged.bus.api.Event
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent
import net.neoforged.neoforge.network.handling.IPayloadContext
import java.util.function.Function

abstract class SkillComponent {

    lateinit var skill: Skill
    var ordinal = 0
        private set
    var isRemoved: Boolean = false
        private set
    var isInitialized = false
        private set

    fun attach(skill: Skill) {
        this.skill = skill
        isRemoved = false
        if (!skill.components.contains(this)) {
            skill.components.add(this)
            ordinal = skill.components.size - 1
        }
        if (!onAttach()) detach()
        else isInitialized = true
    }

    fun tick() {
        if (!isRemoved) onTick()
    }

    fun detach() {
        isRemoved = true
        if (isInitialized) onDetach()
    }

    fun handleEvent(event: Event) {
        if (!isRemoved) onEvent(event)
    }

    fun physicsTick() {
        if (!isRemoved) onPhysicsTick()
    }

    fun copy(): SkillComponent = codec.codec().decode(JsonOps.INSTANCE, CODEC.encodeStart(JsonOps.INSTANCE, this).orThrow).orThrow.first

    protected open fun onAttach(): Boolean = true

    protected open fun onTick() {}

    protected open fun onDetach() {}

    protected open fun onUninitializedDetach() {}

    open fun onHurt(event: LivingIncomingDamageEvent) {}

    open fun onTargetHurt(event: LivingIncomingDamageEvent) {}

    open fun onDamage(event: LivingDamageEvent) {}

    open fun onTargetDamage(event: LivingDamageEvent) {}

    open fun onKnockBack(event: LivingKnockBackEvent) {}

    open fun onTargetKnockBack(event: LivingKnockBackEvent) {}

    protected open fun onEvent(event: Event) {}

    protected open fun onPhysicsTick() {}

    open fun sync(data: CompoundTag, context: IPayloadContext) {}

    // 实用方法
    protected fun <T: SkillComponent> List<T>.attachAll(provider: (SkillComponent) -> Unit = {}) = forEach { provider.invoke(it); it.attach(skill) }

    abstract val codec: MapCodec<out SkillComponent>

    companion object {
        val CODEC = SparkRegistries.SKILL_COMPONENT_CODEC.byNameCodec()
            .dispatch(
                SkillComponent::codec,
                Function.identity()
            )
    }

}