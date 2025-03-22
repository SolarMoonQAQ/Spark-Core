package cn.solarmoon.spark_core.camera

import cn.solarmoon.spark_core.event.EntityTurnEvent
import net.minecraft.client.Minecraft
import net.minecraft.util.Mth
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.Mob
import net.minecraft.world.entity.player.Player
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.CalculateDetachedCameraDistanceEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderPlayerEvent
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.event.entity.EntityEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import org.joml.Vector2f

object CameraAdjuster {

    private val CAMERA_TURN: Vector2f = Vector2f()

//    @SubscribeEvent
//    private fun tick(event: EntityTickEvent.Pre) {
//        val entity = event.entity
//        if (entity is Player) return
//        entity.setCameraLock(false)
//    }

//    @SubscribeEvent
//    private fun tickP(event: EntityTickEvent.Post) {
//        val entity = event.entity
//        if (entity is Player) return
//        if (entity.isCameraLocked()) {
//            entity.yRot = entity.persistentData.getFloat("yRot")
//            entity.yHeadRot = entity.persistentData.getFloat("yHeadRot")
//        } else {
//            entity.persistentData.putFloat("yRot", entity.yRot)
//            entity.persistentData.putFloat("yHeadRot", entity.yHeadRot)
//        }
//    }

//    @SubscribeEvent
//    private fun tick(event: ClientTickEvent.Pre) {
//        val player = Minecraft.getInstance().player ?: return
//        player.setCameraLock(false)
//    }

    @SubscribeEvent
    private fun lockHeadTurn(event: EntityTurnEvent) {
        val entity = event.entity
        val xRot = event.xRot.toFloat()
        val yRot = event.yRot.toFloat()
        if (entity.isCameraLocked()) {
            if (entity is Player && entity.isLocalPlayer) CAMERA_TURN.add(xRot, yRot)
            event.isCanceled = true
        } else if (CAMERA_TURN != Vector2f()) {
            val x = CAMERA_TURN.x.toDouble()
            val y = CAMERA_TURN.y.toDouble()
            CAMERA_TURN.set(0f)
            entity.turn(y, x)
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

fun Entity.setCameraLock(boolean: Boolean) {
    persistentData.putBoolean("CameraLock", boolean)
}

fun Entity.isCameraLocked() = persistentData.getBoolean("CameraLock")