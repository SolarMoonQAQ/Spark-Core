package cn.solarmoon.spark_core.skill.module.body_binder

import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.mojang.serialization.MapCodec
import net.minecraft.world.phys.Vec2
import java.util.function.Function
import kotlin.properties.Delegates

abstract class RigidBodyBinder {

    var body: PhysicsRigidBody? = null
        private set
    var owner: PhysicsHost? = null
        private set
    var collideEnableCheck by Delegates.observable(false) { _, old, new ->
        if (old != new) {
            if (new) onBoxEntry()
            else onBoxExit()
        }
    }

    abstract val activeTime: List<Vec2>

    abstract val shape: CollisionShape

    protected abstract fun createBody(owner: PhysicsHost): PhysicsRigidBody

    abstract val codec: MapCodec<out RigidBodyBinder>

    abstract fun copy(): RigidBodyBinder

    protected open fun onBoxEntry() {
        body?.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_01
    }

    protected open fun onBoxExit() {
        body?.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
    }

    open fun physicsTick(time: Double) {
        activeTime.forEachIndexed { index, range ->
            collideEnableCheck = time in range.x.toDouble()..range.y.toDouble()
        }
    }

    open fun remove() {
        collideEnableCheck = false
        body?.name?.let { owner?.removeBody(it) }
    }

    fun create(owner: PhysicsHost): PhysicsRigidBody {
        this.owner = owner
        body = createBody(owner)
        return body!!
    }

    companion object {
        val CODEC = SparkRegistries.RIGID_BODY_BINDER_CODEC.byNameCodec()
            .dispatch(
                RigidBodyBinder::codec,
                Function.identity()
            )
    }

}