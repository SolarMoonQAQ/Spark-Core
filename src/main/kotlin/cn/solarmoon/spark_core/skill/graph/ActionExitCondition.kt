package cn.solarmoon.spark_core.skill.graph

interface ActionExitCondition {
    fun check(controller: ActionController): Boolean

    object True : ActionExitCondition {
        override fun check(controller: ActionController): Boolean = true
    }

    object False : ActionExitCondition {
        override fun check(controller: ActionController): Boolean = false
    }
}