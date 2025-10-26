package cn.solarmoon.spark_core.js.molang

import cn.solarmoon.spark_core.animation.anim.AnimInstance
import net.minecraft.Util
import net.minecraft.network.codec.ByteBufCodecs
import org.joml.Vector3f

data class Vector3js(
    val x: JSMolangValue,
    val y: JSMolangValue,
    val z: JSMolangValue
) {

    fun eval(anim: AnimInstance): Vector3f {
        val x = x.evalAsDouble(anim)
        val y = y.evalAsDouble(anim)
        val z = z.evalAsDouble(anim)
        return Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
    }

    companion object {
        val CODEC = JSMolangValue.CODEC.listOf()
            .comapFlatMap(
                { list ->
                    Util.fixedSize(list, 3).map { Vector3js(it[0], it[1], it[2]) }
                },
                { v -> listOf(v.x, v.y, v.z) }
            )

        val STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC)
    }

}