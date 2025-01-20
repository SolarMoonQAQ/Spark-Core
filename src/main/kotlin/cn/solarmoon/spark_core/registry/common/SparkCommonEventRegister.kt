package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.play.AnimTicker
import cn.solarmoon.spark_core.animation.preset_anim.CommonAnimApplier
import cn.solarmoon.spark_core.animation.preset_anim.UseAnimApplier
import cn.solarmoon.spark_core.entity.preinput.PreInputApplier
import cn.solarmoon.spark_core.flag.FlagApplier
import cn.solarmoon.spark_core.phys.PresetBodyApplier
import cn.solarmoon.spark_core.phys.thread.PhysThreadApplier
import cn.solarmoon.spark_core.skill.controller.SkillControllerApplier
import net.neoforged.neoforge.common.NeoForge

object SparkCommonEventRegister {

    @JvmStatic
    fun register() {
        add(AnimTicker)
        add(PreInputApplier)
        add(PhysThreadApplier)
        add(PresetBodyApplier)
        add(SkillControllerApplier)
        add(CommonAnimApplier)
        add(UseAnimApplier)
        add(FlagApplier)
    }

    private fun add(event: Any) {
        NeoForge.EVENT_BUS.register(event)
    }

}