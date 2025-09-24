package cn.solarmoon.spark_core.physics.component

import cn.solarmoon.spark_core.registry.common.SparkCollisionObjectTypes
import cn.solarmoon.spark_core.registry.common.SparkDiffSyncSchemas
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.level.Level

class RigidBodyComponent(
    name: String,
    authority: Authority,
    shape: CollisionShape,
    level: Level,
): AbstractRigidBodyComponent<PhysicsRigidBody>(name, authority, level, SparkDiffSyncSchemas.RIGID_BODY_DATA.get(), SparkCollisionObjectTypes.RIGID_BODY.get(), PhysicsRigidBody(shape)) {



}