package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.animation.anim.play.layer.AnimController
import cn.solarmoon.spark_core.animation.model.ModelController
import cn.solarmoon.spark_core.particle.AnimatableShadowParticle
import cn.solarmoon.spark_core.particle.SpaceWarpParticle
import cn.solarmoon.spark_core.physics.CollisionGroups
import cn.solarmoon.spark_core.physics.body.RigidBodyEntity
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.preinput.PreInput
import cn.solarmoon.spark_core.skill.Skill
import cn.solarmoon.spark_core.state_machine.StateMachineHandler
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import cn.solarmoon.spark_core.util.BlackBoard
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import net.minecraft.resources.ResourceLocation
import net.minecraft.world.entity.EntityType
import net.minecraft.world.entity.MobCategory
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object SparkParticles {
    @JvmStatic
    fun register() {}

    val ANIMATABLE_SHADOW = SparkCore.REGISTER.particle<AnimatableShadowParticle.Option>()
        .id("animatable_shadow")
        .bound(true, AnimatableShadowParticle.Option::codec, AnimatableShadowParticle.Option::streamCodec)
        .build()

    val SPACE_WARP = SparkCore.REGISTER.particle<SpaceWarpParticle.Option>()
        .id("space_warp")
        .bound(true, SpaceWarpParticle.Option::codec, SpaceWarpParticle.Option::streamCodec)
        .build()

}