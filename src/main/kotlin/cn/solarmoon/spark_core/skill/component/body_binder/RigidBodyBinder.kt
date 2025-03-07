package cn.solarmoon.spark_core.skill.component.body_binder

import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.skill.SkillTimeLine
import cn.solarmoon.spark_core.skill.component.SkillComponent
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import kotlin.properties.Delegates

abstract class RigidBodyBinder(
    val activeTime: List<SkillTimeLine.Stamp>,
    val onBodyActive: List<SkillComponent> = listOf(),
    val onBodyInactive: List<SkillComponent> = listOf()
): SkillComponent() {

    lateinit var body: PhysicsRigidBody
        protected set
    lateinit var owner: PhysicsHost
        protected set
    var collideEnableCheck by Delegates.observable(false) { _, old, new ->
        if (old != new) {
            if (new) onBoxEntry()
            else onBoxExit()
        }
    }
        protected set

    abstract val shape: CollisionShape

    protected abstract fun createBody(owner: PhysicsHost): PhysicsRigidBody

    override fun onAttach(): Boolean {
        owner = skill.holder as? PhysicsHost ?: return false
        body = createBody(owner)
        skill.physicsBodies.add(body)
        return true
    }

    private var actives = listOf<SkillComponent>()
    private var inactives = listOf<SkillComponent>()

    protected open fun onBoxEntry() {
        body.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_01
        actives = onBodyActive.map { it.copy() }
        actives.forEach { it.attach(skill) }
        inactives.forEach { it.detach() }
    }

    protected open fun onBoxExit() {
        body.collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
        inactives = onBodyInactive.map { it.copy() }
        inactives.forEach { it.attach(skill) }
        actives.forEach { it.detach() }
    }

    override fun onPhysicsTick() {
        collideEnableCheck = skill.timeline.match(activeTime)
    }

    override fun onDetach() {
        collideEnableCheck = false
        skill.physicsBodies.remove(body)
        owner.removeBody(body.name)
    }

    companion object {
        val CODEC = SkillComponent.CODEC.xmap({ it as RigidBodyBinder }, { it })
    }

}