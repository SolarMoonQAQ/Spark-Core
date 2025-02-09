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
    val boneName: String,
    val size: Vector3f,
    val offset: Vector3f,
    override val timeType: String,
    override val activeTime: List<Vec2>,
    children: List<SkillComponent> = listOf()
): BaseRigidBodyBoundComponent(children) {

    val box = CompoundCollisionShape().apply {
        addChildShape(BoxCollisionShape(size.div(2f).toBVector3f()), offset.toBVector3f())
    }

    override fun createBody(owner: PhysicsHost): PhysicsRigidBody {
        return owner.bindBody(PhysicsRigidBody("${UUID.randomUUID()}", owner, box)) {
            isContactResponse = false
            setGravity(Vector3f().toBVector3f())
            addPhysicsTicker(RotateAroundHostTicker())
            removeCollideWithGroup(PhysicsCollisionObject.COLLISION_GROUP_01)
        }
    }

    override val codec: MapCodec<out SkillComponent> = CODEC

    override fun copy(): SkillComponent {
        return BoxAroundHolderComponent(boneName, size, offset, timeType, activeTime, children)
    }

    companion object {
        val CODEC: MapCodec<BoxAroundHolderComponent> = RecordCodecBuilder.mapCodec {
            it.group(
                Codec.STRING.fieldOf("bone_name").forGetter { it.boneName },
                ExtraCodecs.VECTOR3F.fieldOf("size").forGetter { it.size },
                ExtraCodecs.VECTOR3F.fieldOf("offset").forGetter { it.offset },
                Codec.STRING.fieldOf("time_type").forGetter { it.timeType },
                SerializeHelper.VEC2_CODEC.listOf().fieldOf("active_time").forGetter { it.activeTime },
                SkillComponent.CODEC.listOf().optionalFieldOf("children", listOf()).forGetter { it.children }
            ).apply(it, ::BoxAroundHolderComponent)
        }
    }

}