package cn.solarmoon.spark_core.phys

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.IEntityAnimatable
import cn.solarmoon.spark_core.entity.attack.AttackSystem
import cn.solarmoon.spark_core.phys.thread.getPhysLevel
import cn.solarmoon.spark_core.registry.common.SparkVisualEffects
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import org.joml.Matrix4f
import org.joml.Quaterniond
import org.ode4j.math.DVector3
import org.ode4j.ode.DBody
import org.ode4j.ode.DBox
import org.ode4j.ode.DContactBuffer
import org.ode4j.ode.DGeom
import org.ode4j.ode.OdeHelper
import java.awt.Color

fun createEntityBoundingBoxBody(type: BodyType, owner: Entity, level: Level, provider: DBody.() -> Unit = {}) =
    OdeHelper.createBody(type, owner, "body", false, level.getPhysLevel().world).apply {

        val geom = OdeHelper.laterCreateBox(this, level.getPhysLevel().world, DVector3())

        onPhysTick {
            val bb = owner.boundingBox
            geom.lengths = DVector3(bb.xsize, bb.ysize, bb.zsize)
            position = bb.center.toDVector3()
            if (level.isClientSide) SparkVisualEffects.GEOM.getRenderableBox(geom.uuid.toString()).refresh(geom)
        }

        provider.invoke(this)
    }

fun createAnimatedPivotBody(boneName: String, type: BodyType, owner: IAnimatable<*>, level: Level, provider: DBody.() -> Unit = {}) =
    OdeHelper.createBody(type, owner, boneName, false, level.getPhysLevel().world).apply {

        val geom = OdeHelper.laterCreateBox(this, level.getPhysLevel().world, DVector3())

        onPhysTick {
            position = owner.getWorldBonePivot(boneName).toDVector3()
            quaternion = owner.getWorldBoneMatrix(boneName).getUnnormalizedRotation(Quaterniond()).toDQuaternion()
            if (level.isClientSide) SparkVisualEffects.GEOM.getRenderableBox(geom.uuid.toString()).refresh(geom)
        }

        provider.invoke(this)
    }

fun createAnimatedCubeBody(boneName: String, type: BodyType, owner: IAnimatable<*>, level: Level, provider: DBody.() -> Unit = {}) =
    OdeHelper.createBody(type, owner, boneName, false, level.getPhysLevel().world).apply {

        val geoms = mutableListOf<DBox>()
        val bone = owner.modelIndex.model.getBone(boneName)
        repeat(bone.cubes.size) { geoms.add(OdeHelper.laterCreateBox(this, level.getPhysLevel().world, DVector3())) }

        onPhysTick {
            position = owner.getWorldBonePivot(boneName).toDVector3()
            quaternion = owner.getWorldBoneMatrix(boneName).getUnnormalizedRotation(Quaterniond()).toDQuaternion()

            bone.cubes.forEachIndexed { index, cube ->
                val box = geoms[index]
                box.lengths = cube.size.toDVector3()
                box.offsetRotation = cube.rotation.toRadians().toRotationMatrix().toDMatrix3()
                box.offsetPosition = cube.getTransformedCenter(Matrix4f()).sub(bone.pivot.toVector3f()).toDVector3()
                if (level.isClientSide) SparkVisualEffects.GEOM.getRenderableBox(box.uuid.toString()).refresh(box)
            }
        }

        provider.invoke(this)
    }

fun createEntityAnimatedAttackBody(boneName: String, type: BodyType, owner: IEntityAnimatable<*>, level: Level, attackCallBack: AttackCallBack, provider: DBody.() -> Unit = {}) =
    createAnimatedPivotBody(boneName, type, owner, level) {

        firstGeom.isPassFromCollide = true
        disable()

        firstGeom.onCollide { o2, buffer ->
            val successAttack = attackCallBack.doAttack(firstGeom, o2, buffer) {
                attackCallBack.whenAboutToAttack(firstGeom, o2, buffer)

                if (level.isClientSide) {
                    SparkVisualEffects.GEOM.getRenderableBox(firstGeom.uuid.toString()).setColor(Color.RED)
                    SparkVisualEffects.GEOM.getRenderableBox(o2.uuid.toString()).setColor(Color.RED)
                }
            }
            if (successAttack) attackCallBack.whenTargetAttacked(firstGeom, o2, buffer)
        }

        onEnable {
            if (!isEnabled) attackCallBack.attackSystem.reset()
        }

        onDisable {
            attackCallBack.attackSystem.reset()
        }

        provider.invoke(this)
    }

open class AttackCallBack(val attackSystem: AttackSystem) {
    open fun doAttack(o1: DGeom, o2: DGeom, buffer: DContactBuffer, actionBeforeAttack: (AttackSystem) -> Unit = {}) = attackSystem.commonGeomAttack(o1, o2, actionBeforeAttack)
    /**
     * 确定攻击将要发起但还没真正执行前
     */
    open fun whenAboutToAttack(o1: DGeom, o2: DGeom, buffer: DContactBuffer) {}

    /**
     * 当已经攻击到目标后
     */
    open fun whenTargetAttacked(o1: DGeom, o2: DGeom, buffer: DContactBuffer) {}
}




