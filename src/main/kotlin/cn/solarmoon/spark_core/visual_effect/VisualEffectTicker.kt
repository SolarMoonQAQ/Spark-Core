package cn.solarmoon.spark_core.visual_effect

import cn.solarmoon.spark_core.event.PhysicsLevelTickEvent
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import cn.solarmoon.spark_core.util.toVec3
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer.Companion.ALL_VISUAL_EFFECTS
import cn.solarmoon.spark_core.visual_effect.trail.TrailInfo
import cn.solarmoon.spark_core.visual_effect.trail.TrailPoint
import net.minecraft.core.Direction
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.player.Player
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import net.neoforged.neoforge.event.tick.PlayerTickEvent
import org.joml.Quaternionf
import org.joml.Vector3f
import java.awt.Color
import kotlin.math.PI

object VisualEffectTicker {

    @SubscribeEvent
    private fun tick(event: ClientTickEvent.Pre) {
        ALL_VISUAL_EFFECTS.forEach { it.tick() }
    }

    private val HAND_TEXTURE = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stone.png")

    @SubscribeEvent
    private fun test(event: EntityTickEvent.Pre) {
        val player = event.entity
        if (player !is Player) return
        if (!player.level().isClientSide) return

        fun checkPlayingOtherAnim(): Boolean {
            val controller = player.animController
            val animNow = controller.getPlayingAnim() ?: return false
            return animNow.index.name.substringBefore(".") != "state"
        }

        if (checkPlayingOtherAnim()) {
            SparkVisualEffects.TRAIL.addPoint(
                "test",
                { player.getWorldBonePivot("rightItem", Vec3(0.0, 0.0, -0.5), it) },
                { player.getWorldBonePivot("rightItem", Vec3(0.0, 0.0, -1.0), it) },
                TrailInfo(HAND_TEXTURE)
            )
//            val start = player.getWorldBonePivot("rightItem", Vec3(0.0, 0.0, -0.5))
//            val end = player.getWorldBonePivot("rightItem", Vec3(0.0, 0.0, -1.0))
//            SparkVisualEffects.TRAIL.addPoint("test", start, end, TrailInfo(HAND_TEXTURE))
        }
    }

    @SubscribeEvent
    private fun physTick(event: PhysicsLevelTickEvent.Pre) {
        ALL_VISUAL_EFFECTS.forEach { it.physTick(event.level) }
    }
}