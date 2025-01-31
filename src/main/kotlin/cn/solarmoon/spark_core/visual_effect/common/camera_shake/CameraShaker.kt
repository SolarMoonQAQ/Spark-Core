package cn.solarmoon.spark_core.visual_effect.common.camera_shake

import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.network.PacketDistributor
import kotlin.math.cos

class CameraShaker: VisualEffectRenderer() {

    private var shakeTick = 0
    private var strength = 0f
    private var frequency = 0f

    /**
     * 注意此方法很容易被所有人一起调用，如果不是想一起震动最好限定目标为此客户端的本地玩家
     */
    fun shake(time: Int, strength: Float, frequency: Float = 3f) {
        // 保证不被低震度覆盖高震度产生违和感
        if(strength > this.strength) {
            this.strength = strength
            this.shakeTick = time
            this.frequency = frequency
        }
    }

    fun shakeToClient(entity: Entity, time: Int, strength: Float, frequency: Float = 3f) {
        if (entity is ServerPlayer) {
            PacketDistributor.sendToPlayer(entity, CameraShakePayload(time, strength, frequency))
        }
    }

    fun clear() {
        strength = 0f
        frequency = 0f
    }

    override fun tick() {
        if (shakeTick > 0) shakeTick--
        else clear()
    }

    override fun physTick(physLevel: PhysicsLevel) {}

    fun setupCamera(event: ViewportEvent.ComputeCameraAngles) {
        val entity = event.camera.entity
        val partialTicks = event.partialTick
        if(shakeTick > 0) {
            val ticksExistedDelta = entity.tickCount + partialTicks
            if(!Minecraft.getInstance().isPaused) {
                event.pitch = (event.pitch + strength * cos(ticksExistedDelta * frequency + 2)).toFloat()
                event.yaw = (event.yaw + strength * cos(ticksExistedDelta * frequency + 1)).toFloat()
                event.roll = (event.roll + strength * cos(ticksExistedDelta * frequency)).toFloat()
            }
        } else {
            clear()
        }
    }

    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {}

}