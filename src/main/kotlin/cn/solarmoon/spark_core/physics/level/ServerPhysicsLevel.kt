package cn.solarmoon.spark_core.physics.level

import net.minecraft.server.level.ServerLevel
import net.minecraft.world.entity.Entity

class ServerPhysicsLevel(
    override val mcLevel: ServerLevel
) : PhysicsLevel("Server PhysicsThread", mcLevel) {

    override fun requestEntities(): List<Entity> {
        return mcLevel.allEntities.toList()
    }

}