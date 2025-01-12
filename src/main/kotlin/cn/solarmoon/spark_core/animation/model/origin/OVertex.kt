package cn.solarmoon.spark_core.animation.model.origin

/**
 * 顶点数据
 */
data class OVertex(
    val x: Float,
    val y: Float,
    val z: Float,
    var u: Float,
    var v: Float
) {
    constructor(x: Float, y: Float, z: Float) : this(x, y, z, 0f, 0f)

    /**
     * 输出带有新uv的顶点数据
     */
    fun remap(u: Float, v: Float): OVertex = OVertex(x, y, z, u, v)

}