package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.LevelPatch
import cn.solarmoon.spark_core.api.physicsLevel
import cn.solarmoon.spark_core.event.PhysicsLevelInitEvent
import cn.solarmoon.spark_core.mixin_interface.ILevelMixin
import net.minecraft.client.multiplayer.ClientLevel
import net.neoforged.bus.api.EventPriority
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.LevelEvent

object ClientPhysicsLevelApplier {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    private fun load(event: LevelEvent.Load) {
        val level = event.level
        if (level is ClientLevel) {
            (level as ILevelMixin).setPhysicsLevel(ClientPhysicsLevel(level, 3))
            level.physicsLevel.start {
                // 广播通知物理世界初始化完成
                NeoForge.EVENT_BUS.post(PhysicsLevelInitEvent(level.physicsLevel))
            }
        }
    }
}
