package cn.solarmoon.spark_core.physics.presets

import cn.solarmoon.spark_core.animation.model.origin.OBone
import cn.solarmoon.spark_core.util.div
import cn.solarmoon.spark_core.util.toBQuaternion
import cn.solarmoon.spark_core.physics.toBVector3f
import cn.solarmoon.spark_core.util.toRadians
import com.jme3.bullet.collision.shapes.BoxCollisionShape
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import org.joml.Matrix4f
import org.joml.Quaternionf

fun CompoundCollisionShape.initWithAnimatedBone(bone: OBone) {
    bone.cubes.forEachIndexed { index, cube ->
        val box = BoxCollisionShape(cube.size.div(2.0).toBVector3f())
        val rot = cube.rotation.toRadians().toVector3f()
        val offsetPosition = cube.getTransformedCenter(Matrix4f()).sub(bone.pivot.toVector3f()).toBVector3f()
        val offsetRotation = Quaternionf().rotateZYX(rot.z, rot.y, rot.x).toBQuaternion()
        val offset = Transform(offsetPosition, offsetRotation, Vector3f())
        val child = addChildShape(box, offset)
        child.cubeBound = index to cube
    }
}