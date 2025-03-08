package cn.solarmoon.spark_core.skill.component

import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.physics.collision.PhysicsEvent
import cn.solarmoon.spark_core.physics.onEvent
import cn.solarmoon.spark_core.physics.presets.callback.AttackCollisionCallback
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.skill.component.body_binder.RigidBodyBinder
import cn.solarmoon.spark_core.skill.payload.SkillComponentPayload
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.nbt.CompoundTag
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent
import net.neoforged.neoforge.network.PacketDistributor
import net.neoforged.neoforge.network.handling.IPayloadContext
import kotlin.apply
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.let

class PhysicsBodyAttackComponent(
    val bodyBinders: List<RigidBodyBinder>,
    val onPreFirstAttack: List<SkillComponent> = listOf(),
    val onPreAttack: List<SkillComponent> = listOf(),
    val onActualHit: List<SkillComponent> = listOf()
): SkillComponent() {

    private var idCache = mutableSetOf<Long>()

    var preAttack: (Entity, Entity, PhysicsCollisionObject, PhysicsCollisionObject, Long, AttackSystem) -> Unit = { attacker, target, o1, o2, manifoldId, attackSystem -> }

    var doAttack: (Entity, Entity, PhysicsCollisionObject, PhysicsCollisionObject, Long, AttackSystem) -> Boolean = { attacker, target, o1, o2, manifoldId, attackSystem -> true }

    override fun onAttach(): Boolean {
        bodyBinders.attachAll()

        bodyBinders.map { it.body }.forEach {
            it.onEvent<PhysicsEvent.OnCollisionInactive> {
                collisionListeners.forEach {
                    if (it is AttackCollisionCallback) {
                        it.attackSystem.reset()
                    }
                }
            }
            it.addCollisionCallback(object : AttackCollisionCallback {
                override val attackSystem: AttackSystem = AttackSystem()
                override fun preAttack(
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    manifoldId: Long
                ) {
                    if (skill.level.isClientSide) return
                    idCache.add(manifoldId)
                    this@PhysicsBodyAttackComponent.preAttack(attacker, target, aBody, bBody, manifoldId, attackSystem)

                    PacketDistributor.sendToAllPlayers(
                        SkillComponentPayload(
                            this@PhysicsBodyAttackComponent,
                            CompoundTag().apply {
                                putInt("id", target.id)
                                skill.addTarget(target)

                                if (attackSystem.attackedEntities.isEmpty()) {
                                    onPreFirstAttack.attachAll()
                                    putBoolean("first", true)
                                }
                            })
                    )

                    onPreAttack.attachAll()
                }

                override fun doAttack(
                    attacker: Entity,
                    target: Entity,
                    aBody: PhysicsCollisionObject,
                    bBody: PhysicsCollisionObject,
                    manifoldId: Long
                ): Boolean {
                    return this@PhysicsBodyAttackComponent.doAttack(attacker, target, aBody, bBody, manifoldId, attackSystem)
                }
            })
        }

        return true
    }

    override fun onTargetDamage(event: LivingDamageEvent) {
        if (event !is LivingDamageEvent.Post) return
        if (event.source.extraData?.manifoldId in idCache) {
            onActualHit.attachAll {
                PacketDistributor.sendToAllPlayers(SkillComponentPayload(this, CompoundTag().apply {
                    putBoolean("h", true)
                }))
            }
        }
    }

    override fun sync(data: CompoundTag, context: IPayloadContext) {
        val level = context.player().level()
        if (data.getBoolean("h")) {
            onActualHit.attachAll()
        } else {
            level.getEntity(data.getInt("id"))?.let { skill.addTarget(it) }
            if (data.getBoolean("first")) onPreFirstAttack.attachAll()
            onPreAttack.attachAll()
        }
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    companion object {
        val CODEC: MapCodec<PhysicsBodyAttackComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                RigidBodyBinder.Companion.CODEC.listOf().fieldOf("bodies").forGetter { it.bodyBinders },
                SkillComponent.Companion.CODEC.listOf().optionalFieldOf("on_pre_first_attack", listOf()).forGetter { it.onPreFirstAttack },
                SkillComponent.Companion.CODEC.listOf().optionalFieldOf("on_pre_attack", listOf()).forGetter { it.onPreAttack },
                SkillComponent.Companion.CODEC.listOf().optionalFieldOf("on_actual_hit", listOf()).forGetter { it.onActualHit }
            ).apply(it, ::PhysicsBodyAttackComponent)
        }
    }

}