package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import com.jme3.bullet.PhysicsSpace
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge

class ClientPhysicsLevel(
    override val mcLevel: ClientLevel
): PhysicsLevel("Client PhysicsThread", mcLevel) {



    val partialTicks: Float = 1f

}