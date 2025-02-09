package cn.solarmoon.spark_core.camera

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.event.EntityTurnEvent
import net.minecraft.client.player.LocalPlayer
import net.minecraft.util.Mth
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import org.joml.Vector2f

object CameraAdjuster {

    @JvmStatic
    val CAMERA_TURN: Vector2f = Vector2f()

    @JvmStatic
    var isActive = false

    @SubscribeEvent
    private fun lockHeadTurn(event: EntityTurnEvent) {
        val entity = event.entity
        val xRot = event.xRot.toFloat()
        val yRot = event.yRot.toFloat()
        if (entity is IEntityAnimatable<*>) {
            val flag1 = entity.animController.getPlayingAnim()?.time != 0.0
            if (flag1 && isActive) {
                if (entity is LocalPlayer) CAMERA_TURN.add(xRot, yRot)
                event.isCanceled = true
            } else if (CAMERA_TURN != Vector2f()) {
                val x = CAMERA_TURN.x.toDouble()
                val y = CAMERA_TURN.y.toDouble()
                CAMERA_TURN.set(0f)
                entity.turn(y, x)
            }
        }
    }

    @SubscribeEvent
    private fun offsetCameraWhenLock(event: ViewportEvent.ComputeCameraAngles) {
        if (CAMERA_TURN != Vector2f()) {
            val f = CAMERA_TURN.x * 0.15f
            val f1 = CAMERA_TURN.y * 0.15f
            val entity = event.camera.entity
            event.yaw = entity.yRot + f1
            event.pitch = Mth.clamp(entity.xRot + f, -90f, 90f)
        }
    }

}