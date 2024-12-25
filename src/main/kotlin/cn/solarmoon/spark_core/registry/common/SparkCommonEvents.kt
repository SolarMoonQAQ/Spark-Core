package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.auto_anim.AutoAnimApplier
import cn.solarmoon.spark_core.animation.anim.play.AnimTicker
import cn.solarmoon.spark_core.entity.preinput.PreInputApplier
import cn.solarmoon.spark_core.entity.skill.SkillControllerApplier
import cn.solarmoon.spark_core.entity.state.EntityStateModifier
import cn.solarmoon.spark_core.phys.attached_body.AttachedBodyApplier
import cn.solarmoon.spark_core.phys.thread.PhysThreadApplier
import net.neoforged.neoforge.common.NeoForge

object SparkCommonEvents {

    @JvmStatic
    fun register() {
        add(AnimTicker())
        add(EntityStateModifier())
        add(PreInputApplier())
        add(AutoAnimApplier())
        add(PhysThreadApplier())
        add(AttachedBodyApplier())
        add(SkillControllerApplier())
    }

    private fun add(event: Any) {
        NeoForge.EVENT_BUS.register(event)
    }

}