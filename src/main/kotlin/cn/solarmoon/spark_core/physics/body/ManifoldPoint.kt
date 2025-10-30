package cn.solarmoon.spark_core.physics.body

import com.jme3.bullet.collision.ManifoldPoints
import com.jme3.math.Vector3f
import com.simsilica.mathd.Vec3d

class ManifoldPoint(
    val id: Long,
    val index: Int
) {

    val appliedImpulse: Float
        get() = ManifoldPoints.getAppliedImpulse(id)

    val appliedImpulseLateral1: Float
        get() = ManifoldPoints.getAppliedImpulseLateral1(id)

    val appliedImpulseLateral2: Float
        get() = ManifoldPoints.getAppliedImpulseLateral2(id)

    val combinedFriction: Float
        get() = ManifoldPoints.getCombinedFriction(id)

    val combinedRestitution: Float
        get() = ManifoldPoints.getCombinedRestitution(id)

    val combinedRollingFriction: Float
        get() = ManifoldPoints.getCombinedRollingFriction(id)

    val combinedSpinningFriction: Float
        get() = ManifoldPoints.getCombinedSpinningFriction(id)

    val contactMotion1: Float
        get() = ManifoldPoints.getContactMotion1(id)

    val contactMotion2: Float
        get() = ManifoldPoints.getContactMotion2(id)

    val distance: Float
        get() = ManifoldPoints.getDistance1(id)

    val flags: Int
        get() = ManifoldPoints.getFlags(id)

    val triangleIndex: Int
        get() = if (index == 0) ManifoldPoints.getIndex0(id) else ManifoldPoints.getIndex1(id)

    val partId: Int
        get() = if (index == 0) ManifoldPoints.getPartId0(id) else ManifoldPoints.getPartId1(id)

    val lifeTime: Int
        get() = ManifoldPoints.getLifeTime(id)

    fun getLateralFrictionDir1(storeVector: Vector3f = Vector3f()): Vector3f {
        ManifoldPoints.getLateralFrictionDir1(id, storeVector)
        return storeVector
    }

    fun getLateralFrictionDir2(storeVector: Vector3f = Vector3f()): Vector3f {
        ManifoldPoints.getLateralFrictionDir2(id, storeVector)
        return storeVector
    }

    fun getLocalPoint(storeVector: Vector3f = Vector3f()): Vector3f {
        if (index == 0) {
            ManifoldPoints.getLocalPointA(id, storeVector)
        } else {
            ManifoldPoints.getLocalPointB(id, storeVector)
        }
        return storeVector
    }

    fun getNormalWorld(storeVector: Vector3f = Vector3f()): Vector3f {
        ManifoldPoints.getNormalWorldOnB(id, storeVector)
        if (index == 0) { //转化为物体A的接触法线
            storeVector.multLocal(-1f)
        }
        return storeVector
    }

    fun getPositionWorld(storeVector: Vector3f = Vector3f()): Vector3f {
        if (index == 0) {
            ManifoldPoints.getPositionWorldOnA(id, storeVector)
        } else {
            ManifoldPoints.getPositionWorldOnB(id, storeVector)
        }
        return storeVector
    }

    fun getPositionWorldDp(storeVector: Vec3d = Vec3d()): Vec3d {
        if (index == 0) {
            ManifoldPoints.getPositionWorldOnADp(id, storeVector)
        } else {
            ManifoldPoints.getPositionWorldOnBDp(id, storeVector)
        }
        return storeVector
    }

    fun setAppliedImpulse(impulse: Float) {
        ManifoldPoints.setAppliedImpulse(id, impulse)
    }

    fun setAppliedImpulseLateral1(impulse: Float) {
        ManifoldPoints.setAppliedImpulseLateral1(id, impulse)
    }

    fun setAppliedImpulseLateral2(impulse: Float) {
        ManifoldPoints.setAppliedImpulseLateral2(id, impulse)
    }

    fun setCombinedFriction(friction: Float) {
        ManifoldPoints.setCombinedFriction(id, friction)
    }

    fun setCombinedRestitution(restitution: Float) {
        ManifoldPoints.setCombinedRestitution(id, restitution)
    }

    fun setCombinedRollingFriction(friction: Float) {
        ManifoldPoints.setCombinedRollingFriction(id, friction)
    }

    fun setCombinedSpinningFriction(friction: Float) {
        ManifoldPoints.setCombinedSpinningFriction(id, friction)
    }

    fun setContactMotion1(motion: Float) {
        ManifoldPoints.setContactMotion1(id, motion)
    }

    fun setContactMotion2(motion: Float) {
        ManifoldPoints.setContactMotion2(id, motion)
    }

    fun setDistance(distance: Float) {
        ManifoldPoints.setDistance1(id, distance)
    }

    fun setFlags(bitmask: Int) {
        ManifoldPoints.setFlags(id, bitmask)
    }

    fun setLateralFrictionDir1(direction: Vector3f) {
        ManifoldPoints.setLateralFrictionDir1(id, direction)
    }

    fun setLateralFrictionDir2(direction: Vector3f) {
        ManifoldPoints.setLateralFrictionDir2(id, direction)
    }

    fun setLocalPoint(locationVector: Vector3f) {
        if (index == 0) {
            ManifoldPoints.setLocalPointA(id, locationVector)
        } else {
            ManifoldPoints.setLocalPointB(id, locationVector)
        }
    }

    fun setNormalWorldOnB(normalVector: Vector3f) {
        ManifoldPoints.setNormalWorldOnB(id, normalVector)
    }

    fun setPositionWorld(locationVector: Vector3f) {
        if (index == 0) {
            ManifoldPoints.setPositionWorldOnA(id, locationVector)
        } else {
            ManifoldPoints.setPositionWorldOnB(id, locationVector)
        }
    }

    fun createTestPoint(): ManifoldPoint {
        val id = ManifoldPoints.createTestPoint()
        return ManifoldPoint(id, 0) // 默认使用index 0
    }

    fun isContactCalcArea3Points(): Boolean {
        return ManifoldPoints.isContactCalcArea3Points()
    }

    fun setContactCalcArea3Points(setting: Boolean) {
        ManifoldPoints.setContactCalcArea3Points(setting)
    }

}