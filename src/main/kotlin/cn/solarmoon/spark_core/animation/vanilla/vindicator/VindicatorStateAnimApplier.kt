package cn.solarmoon.spark_core.animation.vanilla.vindicator

import cn.solarmoon.spark_core.animation.presets.EntityStates
import cn.solarmoon.spark_core.event.ChangePresetAnimEvent
import cn.solarmoon.spark_core.registry.common.SparkTypedAnimations
import net.minecraft.world.entity.monster.AbstractIllager
import net.minecraft.world.entity.monster.Vindicator
import net.neoforged.bus.api.SubscribeEvent

object VindicatorStateAnimApplier {

    @SubscribeEvent
    private fun anim(event: ChangePresetAnimEvent.EntityState) {
        val state = event.state
        val entity = event.entity as? Vindicator ?: return
        val crossed = entity.armPose == AbstractIllager.IllagerArmPose.CROSSED

        when(state) {
            is EntityStates.Idle -> if (!crossed) event.newAnim = SparkTypedAnimations.VINDICATOR_IDLE_COMBAT.get()
            is EntityStates.Walk -> if (!crossed) event.newAnim = SparkTypedAnimations.VINDICATOR_WALK_COMBAT.get()
        }
    }

}