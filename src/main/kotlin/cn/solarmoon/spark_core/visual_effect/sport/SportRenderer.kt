package cn.solarmoon.spark_core.visual_effect.sport

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.github.stephengold.sport.physics.AabbGeometry
import com.github.stephengold.sport.physics.BasePhysicsApp
import com.jme3.bullet.objects.PhysicsRigidBody
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3

class SportRenderer: VisualEffectRenderer() {

    override fun tick() {

    }

    override fun physTick(physLevel: PhysicsLevel) {
    }

    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        val player = mc.player ?: return
        val body = player.getBody("body", PhysicsRigidBody::class) ?: return
        BasePhysicsApp.visualizeShape(body)
    }

}