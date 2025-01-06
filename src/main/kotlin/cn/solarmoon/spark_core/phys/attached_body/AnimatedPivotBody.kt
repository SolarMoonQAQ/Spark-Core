package cn.solarmoon.spark_core.phys.attached_body

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.phys.thread.PhysLevel
import cn.solarmoon.spark_core.phys.thread.getPhysLevel
import cn.solarmoon.spark_core.phys.toDQuaternion
import cn.solarmoon.spark_core.phys.toDVector3
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.world.level.Level
import org.joml.Quaterniond
import org.ode4j.math.DVector3
import org.ode4j.ode.DBody
import org.ode4j.ode.DContactBuffer
import org.ode4j.ode.DGeom
import org.ode4j.ode.OdeHelper

/**
 * ### 跟随动画骨骼枢轴点Body
 * > 会基于该动画体名为[boneName]的骨骼的枢轴点的位置和旋转来生成自定义大小的碰撞方块
 * @param boneName 指定枢轴点所在的骨骼名，在此类中同时也是碰撞体的名称
 */
open class AnimatedPivotBody(
    bodyName: String,
    val boneName: String,
    val level: Level,
    val animatable: IAnimatable<*>
): AttachedBody {

    override val name: String = bodyName
    override val physLevel: PhysLevel = level.getPhysLevel()
    override val body: DBody = OdeHelper.createBody(name, animatable, false, physLevel.physWorld.world)
    val geom = OdeHelper.laterCreateBox(body, physLevel.physWorld, DVector3())

    init {
        body.onTick {
            body.position = animatable.getBonePivot(boneName).toDVector3()
            body.quaternion = animatable.getBoneMatrix(boneName).getUnnormalizedRotation(Quaterniond()).toDQuaternion()
            tick()
            if (level.isClientSide) SparkVisualEffects.GEOM.getRenderableBox(geom.uuid.toString()).refresh(geom)
        }

        geom.onCollide { o2, buffer ->
            onCollide(o2, buffer)
        }
    }

    open fun tick() {}

    open fun onCollide(o2: DGeom, buffer: DContactBuffer) {}

}