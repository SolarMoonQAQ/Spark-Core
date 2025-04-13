package cn.solarmoon.spark_core.js.extension

import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillPhase
import org.graalvm.polyglot.HostAccess
import org.mozilla.javascript.Function

interface JSSkill {

    val skill get() = this as Skill

    val js get() = skill.level.jsEngine

    @HostAccess.Export
    fun getHolderWrapper() = JSHost(js, skill.holder)

    @HostAccess.Export
    fun setPhase(phase: String) = skill.transitionTo(SkillPhase.valueOf(phase.uppercase()))

    @HostAccess.Export
    fun onActive(consumer: Function) = skill.onEvent<SkillEvent.Active> {
        consumer.call(js)
    }

    @HostAccess.Export
    fun onActiveStart(consumer: Function) = skill.onEvent<SkillEvent.ActiveStart> {
        consumer.call(js)
    }

    @HostAccess.Export
    fun onEnd(consumer: Function) = skill.onEvent<SkillEvent.End> {
        consumer.call(js)
    }

    @HostAccess.Export
    fun onLocalInputUpdate(consumer: Function) = skill.onEvent<SkillEvent.LocalInputUpdate> {
        consumer.call(js, it.event)
    }

    fun onTargetActualHit(consumer: Function) = skill.onEvent<SkillEvent.TargetActualHurt> {
        consumer.call(js, it.event)
    }

}