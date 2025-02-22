package cn.solarmoon.spark_core.skill.component.body_binder

import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.presets.ticker.RotateAroundHostTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.phys.Vec2
import org.joml.Vector3f
import java.util.UUID

class BoxAroundHolderBinder(
    val size: Vector3f,
    val offset: Vector3f,
    override val activeTime: List<Vec2> = listOf()
): RigidBodyBinder() {

    override val shape = CompoundCollisionShape().apply {
        addChildShape(BoxCollisionShape(size.div(2f, Vector3f()).toBVector3f()), offset.toBVector3f())
    }

    override fun createBody(owner: PhysicsHost): PhysicsRigidBody {
        return owner.bindBody(PhysicsRigidBody("${UUID.randomUUID()}", owner, shape)) {
            isContactResponse = false
            setGravity(Vector3f().toBVector3f())
            addPhysicsTicker(RotateAroundHostTicker())
            collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
        }
    }

    override val codec: MapCodec<out RigidBodyBinder> = CODEC

    companion object {
        val CODEC: MapCodec<BoxAroundHolderBinder> = RecordCodecBuilder.mapCodec {
            it.group(
                ExtraCodecs.VECTOR3F.fieldOf("size").forGetter { it.size },
                ExtraCodecs.VECTOR3F.fieldOf("offset").forGetter { it.offset },
                SerializeHelper.VEC2_CODEC.listOf().fieldOf("active_time").forGetter { it.activeTime }
            ).apply(it, ::BoxAroundHolderBinder)
        }
    }

}