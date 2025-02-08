package cn.solarmoon.spark_core.skill.component.collision

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.component.SkillComponent
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.phys.Vec2
import kotlin.properties.Delegates

abstract class BaseBoxBoundComponent: SkillComponent {

    lateinit var body: PhysicsRigidBody
    abstract val activeTime: List<Vec2>
    abstract val collisionComponents: List<CollisionComponent>

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

    override fun onActive(skill: SkillInstance): Boolean {
        val animatable = skill.holder as? IEntityAnimatable<*> ?: return false
        val entity = animatable.animatable
        body = createBody(entity)
        collisionComponents.forEach { it.apply(skill, body) }

        skill.context.animation?.apply {
            activeTime.forEachIndexed { index, range ->
                onPhysTick {
                    collideEnableCheck = time in range.x.toDouble()..range.y.toDouble()
                }
            }
        }

        return true
    }

    override fun onUpdate(skill: SkillInstance): Boolean {
        if (skill.context.animation == null) {
            activeTime.forEachIndexed { index, range ->
                collideEnableCheck = skill.runTime.toDouble() in range.x.toDouble()..range.y.toDouble()
            }
        }
        return true
    }

    override fun onStop(skill: SkillInstance): Boolean {
        body.name.let { (skill.holder as? PhysicsHost)?.removeBody(it) }
        return true
    }

}