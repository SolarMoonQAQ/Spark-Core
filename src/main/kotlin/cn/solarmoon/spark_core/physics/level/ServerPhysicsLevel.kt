package cn.solarmoon.spark_core.physics.level

import cn.solarmoon.spark_core.event.PhysicsEntityTickEvent
import com.jme3.bullet.PhysicsSpace
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.Level
import net.neoforged.neoforge.common.NeoForge

class ServerPhysicsLevel(
    override val mcLevel: ServerLevel
): PhysicsLevel("Server PhysicsThread", mcLevel) {



}