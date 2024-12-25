package cn.solarmoon.spark_core.phys.attached_body

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.phys.thread.getPhysWorld
import cn.solarmoon.spark_core.phys.toDQuaternion
import cn.solarmoon.spark_core.phys.toDVector3
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.world.level.Level
import org.joml.Matrix4f
import org.joml.Quaterniond
import org.ode4j.math.DVector3
import org.ode4j.ode.DBody
import org.ode4j.ode.DBox
import org.ode4j.ode.OdeHelper
import java.util.UUID

/**
 * ### 贴合动画块Body
 * > 会基于该动画体名为[name]的骨骼所包含的每个块来生成几何体并随时贴合。
 *
 * 注意：
 * - 此方法并不像[EntityBoundingBoxBody]一样自动给予，为了性能考虑请手动给予指定的可碰撞骨骼（因为一些复杂模型动辄上百上千个骨骼），并请尽量保证body数量上的优化，比如可在BlockBench中创建多个不可见的骨骼以涵盖主要的可碰撞部位
 * - 由于[EntityBoundingBoxBody]会在生物加入世界时自动给予，如果想要抛弃原有的可碰撞内容，请读取[cn.solarmoon.spark_core.registry.common.SparkAttachments.BODY]并手动删除
 * @param name 想要生成贴合块的骨骼名，也是碰撞体的名称
 */
class AnimatedCubeBody(
    override val name: String,
    val level: Level,
    val holder: IAnimatable<*>,
): AttachedBody {

    val uuid = UUID.randomUUID()
    val entity = holder.animatable
    override val body: DBody = OdeHelper.createBody(name, holder, false, level.getPhysWorld().world)
    val geoms = mutableListOf<DBox>()

    init {
        val bone = holder.animData.model.getBone(name)
        repeat(bone.cubes.size) { geoms.add(OdeHelper.laterCreateBox(body, level.getPhysWorld(), DVector3())) }

        body.onTick {
            body.position = holder.getBonePivot(name).toDVector3()
            body.quaternion = holder.getBoneMatrix(name).getUnnormalizedRotation(Quaterniond()).toDQuaternion()

            bone.cubes.forEachIndexed { index, cube ->
                val box = geoms[index]
                box.lengths = cube.size.toDVector3()
                box.offsetPosition = cube.getTransformedCenter(Matrix4f()).sub(bone.pivot.toVector3f()).toDVector3()
                if (level.isClientSide) SparkVisualEffects.GEOM.getRenderableBox("$uuid: Bounding Box $name").refresh(box)
            }
        }
    }

}