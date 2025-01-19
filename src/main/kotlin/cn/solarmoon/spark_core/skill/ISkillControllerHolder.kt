package cn.solarmoon.spark_core.skill

interface ISkillControllerHolder<T> {

    var skillController: SkillController<T>?

    val allSkillControllers: Map<String, SkillController<T>>

    fun switchTo(name: String?) {
        skillController?.onExit()
        skillController = allSkillControllers[name]
        skillController?.onEntry()
    }

}