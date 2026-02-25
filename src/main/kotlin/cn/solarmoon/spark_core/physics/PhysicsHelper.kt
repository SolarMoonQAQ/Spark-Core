package cn.solarmoon.spark_core.physics

import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.math.Matrix3f
import com.jme3.math.Quaternion
import com.jme3.math.Transform
import com.jme3.math.Vector3f
import com.jme3.system.JmeSystem
import com.jme3.system.Platform.Os.*
import net.minecraft.world.phys.Vec3
import net.neoforged.fml.ModLoadingException
import net.neoforged.fml.ModLoadingIssue
import org.joml.Matrix4f

fun Vec3.toBVector3f() = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())

fun org.joml.Vector3f.toBVector3f() = Vector3f(x, y ,z)

fun org.joml.Matrix3f.toBMatrix3f() = Matrix3f(
    m00, m01, m02,
    m10, m11, m12,
    m20, m21, m22
)

fun Matrix3f.toMatrix3f() = org.joml.Matrix3f(
    get(0, 0), get(0, 1), get(0, 2),
    get(1, 0), get(1, 1), get(1, 2),
    get(2, 0), get(2, 1), get(2, 2)
)