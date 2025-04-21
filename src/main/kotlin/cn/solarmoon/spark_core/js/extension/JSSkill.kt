package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillPhase
import mezz.jei.api.recipe.IFocus
import net.minecraft.world.entity.Entity
import org.mozilla.javascript.Function

interface JSSkill {

    val skill get() = this as Skill

    val js get() = skill.level.jsEngine

    fun getHolderWrapper() = JSHost(js, skill.holder)

    fun setPhase(phase: String) = skill.transitionTo(SkillPhase.valueOf(phase.uppercase()))

    fun addTarget(entity: Entity) {
        if (skill.level.isClientSide) return
        skill.targetPool.addTarget(entity, true)
    }

    fun removeTarget(entity: Entity) {
        if (skill.level.isClientSide) return
        skill.targetPool.removeTarget(entity, true)
    }

    fun onActive(consumer: Function) = skill.onEvent<SkillEvent.Active> {
        consumer.call(js)
    }

    fun onActiveStart(consumer: Function) = skill.onEvent<SkillEvent.ActiveStart> {
        consumer.call(js)
    }

    fun onEnd(consumer: Function) = skill.onEvent<SkillEvent.End> {
        consumer.call(js)
    }

    fun onLocalInputUpdate(consumer: Function) = skill.onEvent<SkillEvent.LocalInputUpdate> {
        consumer.call(js, it.event)
    }

    fun onTargetHurt(consumer: Function) = skill.onEvent<SkillEvent.TargetHurt> {
        consumer.call(js, it.event)
    }

    fun onTargetActualHurtPre(consumer: Function) = skill.onEvent<SkillEvent.TargetActualHurt.Pre> {
        consumer.call(js, it.event)
    }

    fun onTargetActualHurtPost(consumer: Function) = skill.onEvent<SkillEvent.TargetActualHurt.Post> {
        consumer.call(js, it.event)
    }

    fun onHurt(consumer: Function) = skill.onEvent<SkillEvent.Hurt> {
        consumer.call(js, it.event)
    }

    fun onTargetKnockBack(consumer: Function) = skill.onEvent<SkillEvent.TargetKnockBack> {
        consumer.call(js, it.event)
    }

}