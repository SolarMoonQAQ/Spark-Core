package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.animation.anim.auto_anim.AutoAnimRegisterEvent
import cn.solarmoon.spark_core.animation.anim.auto_anim.CommonHitAutoAnim
import cn.solarmoon.spark_core.animation.anim.auto_anim.EntityStateAutoAnim
import cn.solarmoon.spark_core.animation.anim.auto_anim.UseAnimAutoAnim
import cn.solarmoon.spark_core.animation.anim.auto_anim.UseItemAutoAnim
import cn.solarmoon.spark_core.animation.sync.SyncedAnimation
import net.minecraft.world.InteractionHand
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge

object SparkAnimRegister {

    @SubscribeEvent
    private fun reg(event: AutoAnimRegisterEvent.Entity) {
        event.register { a, e -> UseItemAutoAnim(a, e, InteractionHand.MAIN_HAND) }
        event.register { a, e -> UseItemAutoAnim(a, e, InteractionHand.OFF_HAND) }
        event.register { a, e -> UseAnimAutoAnim(a, e, InteractionHand.MAIN_HAND) }
        event.register { a, e -> UseAnimAutoAnim(a, e, InteractionHand.OFF_HAND) }
        event.register { a, e -> EntityStateAutoAnim(a, e) }
        event.register { a, e -> CommonHitAutoAnim(a, e) }
    }

    private fun regSynced() {
        SyncedAnimation.registerAnim()
        CommonHitAutoAnim.registerAnim()
    }

    @JvmStatic
    fun register() {
        regSynced()
        NeoForge.EVENT_BUS.register(this)
    }

}