package cn.solarmoon.spark_core.skill.component.collision

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.skill.component.SkillComponent
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.phys.Vec2
import kotlin.properties.Delegates

abstract class BaseRigidBodyBoundComponent: SkillComponent() {

    private lateinit var body: PhysicsRigidBody
    abstract val activeTime: List<Vec2>
    abstract val flag: String

    var collideEnableCheck by Delegates.observable(false) { _, old, new ->
        if (old != new) {
            if (new) onBoxEntry()
            else onBoxExit()
        }
    }

    abstract fun createBody(owner: PhysicsHost): PhysicsRigidBody

    fun onBoxEntry() {
        body.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_01
    }

    fun onBoxExit() {
        body.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
    }

    override fun onActive() {
        val animatable = skill.holder as? IEntityAnimatable<*> ?: return
        val entity = animatable.animatable
        body = createBody(entity)

        val bodyList = skill.context.data.getOrPut("rigid_body.$flag") { mutableListOf<PhysicsRigidBody>() } as MutableList<PhysicsRigidBody>
        bodyList.add(body)

        query<AnimInstance>("animation")?.apply {
            activeTime.forEachIndexed { index, range ->
                onPhysTick {
                    collideEnableCheck = time in range.x.toDouble()..range.y.toDouble()
                }
            }
        }
    }

    override fun onUpdate() {
        if (query<AnimInstance>("animation") == null) {
            activeTime.forEachIndexed { index, range ->
                collideEnableCheck = skill.runTime.toDouble() in range.x.toDouble()..range.y.toDouble()
            }
        }
    }

    override fun onEnd() {
        body.name.let { (skill.holder as? PhysicsHost)?.removeBody(it) }
    }

}