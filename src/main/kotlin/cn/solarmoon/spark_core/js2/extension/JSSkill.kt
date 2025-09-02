package cn.solarmoon.spark_core.js2.extension

import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillPhase
import net.minecraft.world.entity.Entity
import org.graalvm.polyglot.Value

interface JSSkill {

    val skill get() = this as Skill
    
    fun setPhase(phase: String) = skill.transitionTo(SkillPhase.valueOf(phase.uppercase()))

    fun addTarget(entity: Entity) {
        if (skill.level.isClientSide) return
        skill.targetPool.addTarget(entity, true)
    }

    fun removeTarget(entity: Entity) {
        if (skill.level.isClientSide) return
        skill.targetPool.removeTarget(entity, true)
    }

    fun onActive(consumer: Value) = skill.onEvent<SkillEvent.Active> {
        consumer.execute()
    }

    fun onActiveStart(consumer: Value) = skill.onEvent<SkillEvent.ActiveStart> {
        consumer.execute()
    }

    fun onEnd(consumer: Value) = skill.onEvent<SkillEvent.End> {
        consumer.execute()
    }

    fun onLocalInputUpdate(consumer: Value) = skill.onEvent<SkillEvent.LocalInputUpdate> {
        consumer.execute(it.event)
    }

    fun onTargetHurt(consumer: Value) = skill.onEvent<SkillEvent.TargetHurt> {
        consumer.execute(it.event)
    }

    fun onTargetActualHurtPre(consumer: Value) = skill.onEvent<SkillEvent.TargetActualHurt.Pre> {
        consumer.execute(it.event)
    }

    fun onTargetActualHurtPost(consumer: Value) = skill.onEvent<SkillEvent.TargetActualHurt.Post> {
        consumer.execute(it.event)
    }

    fun onHurt(consumer: Value) = skill.onEvent<SkillEvent.Hurt> {
        consumer.execute(it.event)
    }

    fun onTargetKnockBack(consumer: Value) = skill.onEvent<SkillEvent.TargetKnockBack> {
        consumer.execute(it.event)
    }

    fun getLocation() = skill.type.registryKey

}