package cn.solarmoon.spark_core.ik.component

import au.edu.federation.caliko.FabrikChain3D
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3

/**
 * Interface for entities that can host and manage IK components.
 * Must also implement IAnimatable to provide necessary model and bone data.
 */
interface IKHost<T : Entity> : IEntityAnimatable<T> { // Inherit from IAnimatable
    val ikManager: IKManager // Provides access to the manager instance

}
