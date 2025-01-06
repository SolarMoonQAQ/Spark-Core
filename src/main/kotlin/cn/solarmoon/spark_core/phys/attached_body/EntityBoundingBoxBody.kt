package cn.solarmoon.spark_core.phys.attached_body

import cn.solarmoon.spark_core.phys.thread.PhysLevel
import cn.solarmoon.spark_core.phys.thread.getPhysLevel
import cn.solarmoon.spark_core.phys.toDVector3
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.ode4j.math.DVector3
import org.ode4j.ode.DBody
import org.ode4j.ode.OdeHelper

/**
 * ### 原版碰撞箱Body
 * > 随时贴合原版实体AABB大小，名称默认为“body”
 */
class EntityBoundingBoxBody(
    val level: Level,
    val entity: Entity
): AttachedBody {

    override val name: String = "body"
    override val physLevel: PhysLevel = level.getPhysLevel()
    override val body: DBody = OdeHelper.createBody(name, entity, false, physLevel.physWorld.world)
    val geom = OdeHelper.laterCreateBox(body, physLevel.physWorld, DVector3())

    init {
        body.onTick {
            val bb = entity.boundingBox
            geom.lengths = DVector3(bb.xsize, bb.ysize, bb.zsize)
            body.position = bb.center.toDVector3()
            if (level.isClientSide) SparkVisualEffects.GEOM.getRenderableBox(geom.uuid.toString()).refresh(geom)
        }
    }

}