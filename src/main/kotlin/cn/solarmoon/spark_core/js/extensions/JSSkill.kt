package cn.solarmoon.spark_core.js.extensions

import cn.solarmoon.spark_core.js.doc.JSClass
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillConfig
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillHost
import cn.solarmoon.spark_core.util.onEvent
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent

@JSClass("Skill")
interface JSSkill {

    val skill get() = this as Skill

    fun addTarget(entity: Entity) {
        if (skill.level.isClientSide) return
        skill.targetPool.addTarget(entity, true)
    }

    fun removeTarget(entity: Entity) {
        if (skill.level.isClientSide) return
        skill.targetPool.removeTarget(entity, true)
    }

    fun js_end() {
        skill.end()
    }

    fun js_getHolder(): SkillHost = skill.holder

    fun js_getLevel(): Level = skill.level

    fun initConfig(consumer: (SkillConfig) -> Unit) = skill.onEvent<SkillEvent.ConfigInit> {
        consumer(it.config)
    }

    fun init(consumer: () -> Unit) = skill.onEvent<SkillEvent.Init> {
        consumer()
    }

    fun onStart(consumer: () -> Unit) = skill.onEvent<SkillEvent.Start> {
        consumer()
    }

    fun onUpdate(consumer: () -> Unit) = skill.onEvent<SkillEvent.Update> {
        consumer()
    }

    fun onEnd(consumer: () -> Unit) = skill.onEvent<SkillEvent.End> {
        consumer()
    }

    fun onLocalInputUpdate(consumer: (MovementInputUpdateEvent) -> Unit) = skill.onEvent<SkillEvent.LocalInputUpdate> {
        consumer(it.event)
    }

    fun onTargetHurt(consumer: (LivingIncomingDamageEvent) -> Unit) = skill.onEvent<SkillEvent.TargetHurt> {
        consumer(it.event)
    }

    fun onTargetActualHurtPre(consumer: (LivingDamageEvent.Pre) -> Unit) = skill.onEvent<SkillEvent.TargetActualHurt.Pre> {
        consumer(it.event)
    }

    fun onTargetActualHurtPost(consumer: (LivingDamageEvent.Post) -> Unit) = skill.onEvent<SkillEvent.TargetActualHurt.Post> {
        consumer(it.event)
    }

    fun onHurt(consumer: (LivingIncomingDamageEvent) -> Unit) = skill.onEvent<SkillEvent.Hurt> {
        consumer(it.event)
    }

    fun onTargetKnockBack(consumer: (LivingKnockBackEvent) -> Unit) = skill.onEvent<SkillEvent.TargetKnockBack> {
        consumer(it.event)
    }

}