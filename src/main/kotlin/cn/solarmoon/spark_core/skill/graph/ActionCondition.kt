package cn.solarmoon.spark_core.skill.graph

import cn.solarmoon.spark_core.skill.SkillHost
import net.minecraft.network.chat.Component

interface ActionCondition {

    fun getDescription(controller: ActionController): Component = Component.empty()

    fun check(controller: ActionController): Boolean

}