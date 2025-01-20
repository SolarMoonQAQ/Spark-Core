package cn.solarmoon.spark_core.skill.controller

import kotlin.collections.get

interface ISkillControllerHolder<T> {

    var skillController: SkillController<out T>?

    val allSkillControllers: MutableMap<String, SkillController<out T>>

    fun switchTo(name: String?) {
        skillController?.onExit()
        skillController = allSkillControllers[name]
        skillController?.onEntry()
    }

}