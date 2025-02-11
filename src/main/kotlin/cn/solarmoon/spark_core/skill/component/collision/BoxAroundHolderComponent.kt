package cn.solarmoon.spark_core.skill.component.collision

import cn.solarmoon.spark_core.data.SerializeHelper
import cn.solarmoon.spark_core.physics.host.PhysicsHost
import cn.solarmoon.spark_core.physics.presets.RotateAroundHostTicker
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.skill.component.SkillComponent
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import net.minecraft.util.ExtraCodecs
import net.minecraft.world.phys.Vec2
import org.joml.Vector3f
import java.util.UUID
import kotlin.apply

class BoxAroundHolderComponent(
    override val flag: String,
    val boneName: String,
    val size: Vector3f,
    val offset: Vector3f,
    override val activeTime: List<Vec2>,
): BaseRigidBodyBoundComponent() {

    val box = CompoundCollisionShape().apply {
        addChildShape(BoxCollisionShape(size.div(2f).toBVector3f()), offset.toBVector3f())
    }

    override fun createBody(owner: PhysicsHost): PhysicsRigidBody {
        return owner.bindBody(PhysicsRigidBody("${UUID.randomUUID()}", owner, box)) {
            isContactResponse = false
            setGravity(Vector3f().toBVector3f())
            addPhysicsTicker(RotateAroundHostTicker())
            collideWithGroups = PhysicsCollisionObject.COLLISION_GROUP_NONE
        }
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return BoxAroundHolderComponent(flag, boneName, size, offset, activeTime)
    }

    companion object {
        val CODEC: MapCodec<BoxAroundHolderComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.STRING.fieldOf("flag").forGetter { it.flag },
                Codec.STRING.fieldOf("bone_name").forGetter { it.boneName },
                ExtraCodecs.VECTOR3F.fieldOf("size").forGetter { it.size },
                ExtraCodecs.VECTOR3F.fieldOf("offset").forGetter { it.offset },
                SerializeHelper.VEC2_CODEC.listOf().fieldOf("active_time").forGetter { it.activeTime }
            ).apply(it, ::BoxAroundHolderComponent)
        }
    }

}