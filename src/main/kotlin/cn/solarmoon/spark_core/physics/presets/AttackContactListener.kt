package cn.solarmoon.spark_core.physics.presets

import com.jme3.bullet.collision.ContactListener
import com.jme3.bullet.collision.PersistentManifolds
import com.jme3.bullet.collision.PhysicsCollisionObject
import net.minecraft.world.entity.LivingEntity

class AttackContactListener: ContactListener {

    override fun onContactEnded(manifoldId: Long) {
        val b = PhysicsCollisionObject.findInstance(PersistentManifolds.getBodyBId(manifoldId))
        (b.owner as? LivingEntity)?.apply {
            level().submitImmediateTask {
                hurt(damageSources().magic(), 1f)
            }
        }
    }

    override fun onContactProcessed(
        pcoA: PhysicsCollisionObject?,
        pcoB: PhysicsCollisionObject?,
        manifoldPointId: Long
    ) {

    }

    override fun onContactStarted(manifoldId: Long) {

    }

}