package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.play.AnimTicker
import cn.solarmoon.spark_core.animation.preset_anim.CommonAnimApplier
import cn.solarmoon.spark_core.entity.preinput.PreInputApplier
import cn.solarmoon.spark_core.phys.attached_body.AttachedBodyApplier
import cn.solarmoon.spark_core.phys.thread.PhysThreadApplier
import cn.solarmoon.spark_core.skill.SkillControllerApplier
import cn.solarmoon.spark_core.animation.preset_anim.UseAnimApplier
import net.neoforged.neoforge.common.NeoForge

object SparkCommonEvents {

    @JvmStatic
    fun register() {
        add(AnimTicker)
        add(PreInputApplier)
        add(PhysThreadApplier)
        add(AttachedBodyApplier)
        add(SkillControllerApplier)
        add(CommonAnimApplier)
        add(UseAnimApplier)
    }

    private fun add(event: Any) {
        NeoForge.EVENT_BUS.register(event)
    }

}