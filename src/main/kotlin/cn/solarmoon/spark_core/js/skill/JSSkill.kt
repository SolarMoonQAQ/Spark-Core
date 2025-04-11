package cn.solarmoon.spark_core.js.skill

import cn.solarmoon.spark_core.js.SparkJS
import cn.solarmoon.spark_core.js.call
import cn.solarmoon.spark_core.js.extension.JSEntity
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.SkillEvent
import cn.solarmoon.spark_core.skill.SkillPhase
import org.graalvm.polyglot.HostAccess
import org.graalvm.polyglot.Value
import org.mozilla.javascript.Function

open class JSSkill(
    val js: SparkJS,
    val skill: Skill
) {

    @HostAccess.Export
    fun getHolder() = JSEntity(js, skill.holder)

    @HostAccess.Export
    fun getLevel() = skill.level

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

    @HostAccess.Export
    fun end() = skill.end()

}