package cn.solarmoon.spark_core.phys

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.registry.common.SparkRegistries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.minecraft.world.entity.Entity
import net.minecraft.world.level.Level
import net.minecraft.world.phys.AABB
import net.neoforged.neoforge.network.PacketDistributor
import org.joml.Vector3d
import org.ode4j.math.DVector3
import org.ode4j.math.DVector3C
import org.ode4j.ode.DAABB
import org.ode4j.ode.DAABBC
import org.ode4j.ode.DBody
import org.ode4j.ode.DBox
import org.ode4j.ode.DContactBuffer
import org.ode4j.ode.DGeom
import org.ode4j.ode.DSpace
import org.ode4j.ode.DWorld
import org.ode4j.ode.OdeHelper
import org.ode4j.ode.internal.DxBody
import org.ode4j.ode.internal.DxGeom
import thedarkcolour.kotlinforforge.neoforge.forge.vectorutil.v3d.toVec3

object DxHelper {

    @JvmStatic
    fun initOde() {
        CoroutineScope(Dispatchers.Default).launch {
            OdeHelper.initODE()
        }
    }

}

inline fun <reified T> DBody.getOwner(): T? {
    return owner as? T
}

/**
 * 复制一个只留存基本几何数据的Box
 */
fun DBox.baseCopy() = OdeHelper.createBox(lengths.copy()).apply {
    position = this@baseCopy.position
    rotation = this@baseCopy.rotation
}

fun DBox.getVertexes(): List<Vector3d> {
    val vertices = MutableList(8) { Vector3d() }
    val halfLengths = lengths.toVector3d().div(2.0)

    // 计算每个顶点的相对位置
    for (i in 0 until 8) {
        val relativePos = Vector3d(
            if (i and 1 == 1) halfLengths.x else -halfLengths.x,
            if (i and 2 == 2) halfLengths.y else -halfLengths.y,
            if (i and 4 == 4) halfLengths.z else -halfLengths.z
        )

        val realPos = DVector3()
        getRelPointPos(relativePos.x, relativePos.y, relativePos.z, realPos)
        vertices[i] = realPos.toVector3d()
    }

    return vertices
}

fun AABB.toDAABB() = DAABB(minX, maxX, minY, maxY, minZ, maxZ)

fun DAABBC.toDBox(space: DSpace) = OdeHelper.createBox(space, lengths).apply { position = center }