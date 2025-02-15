package cn.solarmoon.spark_core.skill.node.leaves.collision

import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimInstance
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.skill.SkillInstance
import cn.solarmoon.spark_core.skill.node.BehaviorNode
import cn.solarmoon.spark_core.skill.node.NodeStatus
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.world.phys.Vec2
import kotlin.properties.Delegates

abstract class BaseRigidBodyBoundComponent: BehaviorNode() {

    private var body: PhysicsRigidBody? = null
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
        body?.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_01
    }

    fun onBoxExit() {
        body?.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
    }

    override fun onStart(skill: SkillInstance) {
        val animatable = skill.holder as? IEntityAnimatable<*> ?: return
        val entity = animatable.animatable
        body = createBody(entity)

        val bodyList = skill.behaviorTree.blackBoard.global.getOrPut("rigid_body.$flag") { mutableListOf<PhysicsRigidBody>() } as MutableList<PhysicsRigidBody>
        bodyList.add(body!!)

        read<AnimInstance>("animation")?.apply {
            if (activeTime.isEmpty()) {
                collideEnableCheck = true
            } else {
                activeTime.forEachIndexed { index, range ->
                    onPhysTick {
                        collideEnableCheck = time in range.x.toDouble()..range.y.toDouble()
                    }
                }
            }
        }
    }

    override fun onTick(skill: SkillInstance): NodeStatus {
        if (read<AnimInstance>("animation") == null) {
            activeTime.forEachIndexed { index, range ->
                collideEnableCheck = skill.runTime.toDouble() in range.x.toDouble()..range.y.toDouble()
            }
        }
        return NodeStatus.RUNNING
    }

    override fun onEnd(skill: SkillInstance) {
        collideEnableCheck = false
        body?.name?.let { (skill.holder as? PhysicsHost)?.removeBody(it) }
    }

}