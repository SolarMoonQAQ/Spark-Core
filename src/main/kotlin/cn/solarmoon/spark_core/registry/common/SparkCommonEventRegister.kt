package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.play.AnimApplier
import cn.solarmoon.spark_core.animation.preset_anim.CommonAnimApplier
import cn.solarmoon.spark_core.animation.preset_anim.PoseAnimApplier
import cn.solarmoon.spark_core.animation.preset_anim.UseAnimApplier
import cn.solarmoon.spark_core.animation.vanilla.PlayerBoneModifier
import cn.solarmoon.spark_core.entity.preinput.PreInputApplier
import cn.solarmoon.spark_core.flag.FlagApplier
import cn.solarmoon.spark_core.physics.host.PhysicsHostApplier
import cn.solarmoon.spark_core.physics.level.PhysicsLevelApplier
import cn.solarmoon.spark_core.skill.controller.SkillControllerApplier
import net.neoforged.neoforge.common.NeoForge

object SparkCommonEventRegister {

    @JvmStatic
    fun register() {
        add(PhysicsLevelApplier)
        add(PhysicsHostApplier)
        add(AnimApplier)
        add(PreInputApplier)
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