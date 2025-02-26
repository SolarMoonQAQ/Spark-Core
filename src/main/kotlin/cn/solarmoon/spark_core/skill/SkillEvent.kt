package cn.solarmoon.spark_core.skill

open class SkillEvent {

    object Active: SkillEvent()

    object Update: SkillEvent()

    object End: SkillEvent()

}