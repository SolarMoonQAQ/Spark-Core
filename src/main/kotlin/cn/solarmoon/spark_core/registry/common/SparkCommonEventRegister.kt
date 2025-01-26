package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.play.AnimApplier
import cn.solarmoon.spark_core.animation.preset_anim.CommonAnimApplier
import cn.solarmoon.spark_core.animation.preset_anim.PoseAnimApplier
import cn.solarmoon.spark_core.animation.preset_anim.UseAnimApplier
import cn.solarmoon.spark_core.animation.vanilla.PlayerBoneModifier
import cn.solarmoon.spark_core.entity.preinput.PreInputApplier
import cn.solarmoon.spark_core.flag.FlagApplier
import cn.solarmoon.spark_core.phys.PresetBodyApplier
import cn.solarmoon.spark_core.phys.thread.PhysThreadApplier
import cn.solarmoon.spark_core.skill.controller.SkillControllerApplier
import net.neoforged.neoforge.common.NeoForge

object SparkCommonEventRegister {

    @JvmStatic
    fun register() {
        add(AnimApplier)
        add(PreInputApplier)
        add(PhysThreadApplier)
        add(PresetBodyApplier)
        add(SkillControllerApplier)
        add(CommonAnimApplier)
        add(UseAnimApplier)
        add(FlagApplier)
        add(PoseAnimApplier)
        add(PlayerBoneModifier)
    }

    private fun add(event: Any) {
        NeoForge.EVENT_BUS.register(event)
    }

}