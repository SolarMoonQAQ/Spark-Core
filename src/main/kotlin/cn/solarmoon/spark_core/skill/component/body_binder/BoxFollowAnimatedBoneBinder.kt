package cn.solarmoon.spark_core.skill.component.body_binder

import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithAnimatedBoneTicker
import cn.solarmoon.spark_core.physics.presets.ticker.MoveWithBonePivotTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.skill.SkillTimeLine
import cn.solarmoon.spark_core.skill.component.SkillComponent
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.ExtraCodecs
import org.joml.Vector3f
import java.util.UUID

class BoxFollowAnimatedBoneBinder(
    val boneName: String,
    val size: Vector3f,
    val offset: Vector3f,
    activeTime: List<SkillTimeLine.Stamp> = listOf(),
    onBodyActive: List<SkillComponent> = listOf(),
    onBodyInactive: List<SkillComponent> = listOf()
): RigidBodyBinder(activeTime, onBodyActive, onBodyInactive) {

    override val shape: CollisionShape = CompoundCollisionShape().apply {
         addChildShape(BoxCollisionShape(size.div(2f, Vector3f()).toBVector3f()), offset.toBVector3f())
    }

    override fun createBody(owner: PhysicsHost): PhysicsRigidBody {
        return owner.bindBody(PhysicsRigidBody("${UUID.randomUUID()}", owner, shape)) {
            isContactResponse = false
            setGravity(Vector3f().toBVector3f())
            addPhysicsTicker(MoveWithBonePivotTicker(boneName, offset.toBVector3f()))
            collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
        }
    }

    override val codec: MapCodec<out RigidBodyBinder> = CODEC

    companion object {
        val CODEC: MapCodec<BoxFollowAnimatedBoneBinder> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.STRING.fieldOf("bone_name").forGetter { it.boneName },
                ExtraCodecs.VECTOR3F.fieldOf("size").forGetter { it.size },
                ExtraCodecs.VECTOR3F.fieldOf("offset").forGetter { it.offset },
                SkillTimeLine.Stamp.CODEC.listOf().fieldOf("active_time").forGetter { it.activeTime },
                SkillComponent.CODEC.listOf().optionalFieldOf("on_body_active", listOf()).forGetter { it.onBodyActive },
                SkillComponent.CODEC.listOf().optionalFieldOf("on_body_inactive", listOf()).forGetter { it.onBodyInactive }
            ).apply(it, ::BoxFollowAnimatedBoneBinder)
        }
    }

}