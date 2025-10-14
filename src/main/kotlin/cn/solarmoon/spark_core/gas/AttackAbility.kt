package cn.solarmoon.spark_core.gas

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.AnimEvent
import cn.solarmoon.spark_core.animation.anim.AnimInstance
import cn.solarmoon.spark_core.animation.anim.animInstance
import cn.solarmoon.spark_core.animation.anim.origin.AnimIndex
import cn.solarmoon.spark_core.entity.attack.AttackContext
import cn.solarmoon.spark_core.entity.attack.CollisionAttackSystem
import cn.solarmoon.spark_core.entity.attack.CollisionHurtData
import cn.solarmoon.spark_core.js.getJSBindings
import cn.solarmoon.spark_core.physics.body.CollisionGroups
import cn.solarmoon.spark_core.physics.body.PhysicsBodyEvent
import cn.solarmoon.spark_core.physics.body.addPhysicsBody
import cn.solarmoon.spark_core.physics.body.attachToBone
import cn.solarmoon.spark_core.physics.body.owner
import cn.solarmoon.spark_core.physics.body.removePhysicsBody
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.div
import cn.solarmoon.spark_core.util.onEvent
import cn.solarmoon.spark_core.util.toVec3
import cn.solarmoon.spark_core.util.triggerEvent
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.world.entity.Entity
import net.minecraft.world.entity.player.Player
import org.graalvm.polyglot.HostAccess

class AttackAbility(
    val animIndex: AnimIndex
): Ability() {

    lateinit var owner: IEntityAnimatable<*>
    lateinit var animation: AnimInstance
    val hitBoxes = mutableMapOf<String, PhysicsRigidBody>()
    val hitContexts = mutableMapOf<String, AttackContext>()

    override fun canActivate(spec: AbilitySpec<*>, context: ActivationContext): ActivationResult {
        val isAnimatable = spec.asc.owner is IEntityAnimatable<*>
        return if (isAnimatable) super.canActivate(spec, context) else ActivationResult(false)
    }

    override fun activate(spec: AbilitySpec<*>, context: ActivationContext) {
        owner = spec.asc.owner as IEntityAnimatable<*>
        animation = animInstance(owner, animIndex) {
            onEvent<AnimEvent.Notify> {
                it.value.context.getJSBindings().putMember("sof", this@AttackAbility)
            }
            onEvent<AnimEvent.End> {
                end(spec)
            }
        }!!
        animation.independentEnter()
    }

    override fun end(spec: AbilitySpec<*>) {
        hitBoxes.values.forEach {
            owner.animatable.level().removePhysicsBody(it)
        }
    }

    @HostAccess.Export
    fun summonHitBox(id: String, name: String, size: DoubleArray, offset: DoubleArray) {
        val context = AttackContext()
        val ownerA = owner
        val body = PhysicsRigidBody(BoxCollisionShape(size.toVec3().div(2.0).toBVector3f())).apply {
            this.name = id
            owner = ownerA.animatable
            isKinematic = true
            collisionGroup = CollisionGroups.TRIGGER
            collideWithGroups = CollisionGroups.PAWN
            onEvent<PhysicsBodyEvent.Collide.Processed> {
                val o1 = it.o1
                val o2 = it.o2
                val aPoint = it.o1Point
                val bPoint = it.o2Point
                if (o1.owner == ownerA && o2.owner is Entity) {
                    val target = o2.owner as Entity

                    (object: CollisionAttackSystem(context, CollisionHurtData(o1, o2, aPoint, bPoint)) {
                        override fun preAttack(target: Entity) {

                        }

                        override fun doAttack(target: Entity): Boolean {
                            if (ownerA is Player) ownerA.attack(target)
                            return true
                        }

                        override fun postAttack(target: Entity) {

                        }

                    }).attack(target)
                }
            }
            attachToBone(this@AttackAbility.owner, name, offset.toVec3().toVector3f())
        }
        hitBoxes[id] = body
        hitContexts[id] = context
        owner.animatable.level().addPhysicsBody(body)
    }

    @HostAccess.Export
    fun disableHitBox(id: String) {
        hitBoxes[id]?.collideWithGroups = CollisionGroups.NONE
    }

}

class AttackAbilityTypeSerializer(
    val animIndex: AnimIndex
): AbilityType.Serializer {

    override val codec: MapCodec<out AbilityType.Serializer> = CODEC

    override fun create(): AbilityType<*> {
        return AbilityType(InstancingPolicy.INSTANCED_PER_ACTOR) { AttackAbility(animIndex) }
    }

    companion object {
        val CODEC = RecordCodecBuilder.mapCodec {
            it.group(
                AnimIndex.CODEC.fieldOf("anim_index").forGetter(AttackAbilityTypeSerializer::animIndex),
            ).apply(it, ::AttackAbilityTypeSerializer)
        }
    }

}