package cn.solarmoon.spark_core.skill

interface SkillState {

    val name: String

    var tickCount: Int

    var times: Int

    fun onEnter(skill: Skill) {}

    fun onUpdate(skill: Skill) {}

    fun onExit(skill: Skill) {}

    companion object {
        val PREPARE = DefaultSkillState("prepare")

        val END = DefaultSkillState("end")
    }

}

open class DefaultSkillState(override val name: String): SkillState {
    override var tickCount: Int = 0
    override var times: Int = 0
}