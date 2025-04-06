package cn.solarmoon.spark_core.js.skill

import cn.solarmoon.spark_core.js.extension.JSEntity
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillPhase
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value

open class JSSkill(
    val skill: Skill
) {

    @HostAccess.Export
    fun getHolder() = JSEntity(skill.holder)

    @HostAccess.Export
    fun getPhase() = skill.phase.name.lowercase()

    @HostAccess.Export
    fun setPhase(phase: String) = skill.transitionTo(SkillPhase.valueOf(phase.uppercase()))

    @HostAccess.Export
    fun getTickCount() = skill.tickCount

    @HostAccess.Export
    fun getWindupTickCount() = skill.windupTickCount

    @HostAccess.Export
    fun getActiveTickCount() = skill.activeTickCount

    @HostAccess.Export
    fun getRecoveryTickCount() = skill.recoveryTickCount

    @HostAccess.Export
    fun onActive(consumer: Value) = skill.onEvent<SkillEvent.Active> {
        consumer.execute()
    }

    @HostAccess.Export
    fun onActiveStart(consumer: Value) = skill.onEvent<SkillEvent.ActiveStart> {
        consumer.execute()
    }

    @HostAccess.Export
    fun onEnd(consumer: Value) = skill.onEvent<SkillEvent.End> {
        consumer.execute()
    }

    @HostAccess.Export
    fun onLocalInputUpdate(consumer: Value) = skill.onEvent<SkillEvent.LocalInputUpdate> {
        consumer.execute(it.event)
    }

    @HostAccess.Export
    fun end() = skill.end()

}