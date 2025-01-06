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

inline fun <reified T> DBody.getOwner(): T? {
    return owner as? T
}