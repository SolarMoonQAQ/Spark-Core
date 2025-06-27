package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.physics.collision.PhysicsCollisionObjectTicker
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.material.FluidState
import java.util.concurrent.ConcurrentHashMap

class ChunkRigidBody(
    val chunk: ChunkAccess,
    override val physicsLevel: PhysicsLevel
) : PhysicsHost {
//    val blocks: ConcurrentHashMap<BlockPos, BlockState>
//    val blockBody:PhysicsRigidBody
//    val fluidBody:PhysicsRigidBody

    init {
//        chunk.getSection(0).getBlockState()
    }

}