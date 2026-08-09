package cn.solarmoon.spark_core.compat.accelerated_rendering

import cn.solarmoon.spark_core.animation.model.origin.OCube
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.IBufferGraph
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.builders.VertexConsumerExtension
import com.github.argon4w.acceleratedrendering.core.buffers.accelerated.renderers.IAcceleratedRenderer
import com.github.argon4w.acceleratedrendering.core.meshes.IMesh
import com.github.argon4w.acceleratedrendering.core.meshes.collectors.CulledMeshCollector
import com.github.argon4w.acceleratedrendering.features.entities.AcceleratedEntityRenderingFeature
import com.mojang.blaze3d.vertex.VertexConsumer
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap
import org.joml.Matrix3f
import org.joml.Matrix4f
import org.joml.Vector3f

object ARCubeRenderer : IAcceleratedRenderer<OCube> {

    val cubeMeshes: MutableMap<OCube, MutableMap<IBufferGraph, IMesh>> = Reference2ObjectOpenHashMap()

    override fun render(
        vertexConsumer: VertexConsumer,
        cube: OCube,
        transform: Matrix4f,
        normal: Matrix3f,
        light: Int,
        overlay: Int,
        color: Int
    ) {
        val extension = VertexConsumerExtension.getAccelerated(vertexConsumer)
        extension.beginTransform(transform, normal)
        cubeMeshes
            .computeIfAbsent(cube) { mutableMapOf() }
            .computeIfAbsent(extension) {
                val culledMeshCollector = CulledMeshCollector(extension)
                val meshBuilder: VertexConsumer = extension.decorate(culledMeshCollector)
                for (i in cube.polygonSet.indices) {
                    val polygon = cube.polygonSet[i]
                    // 静态 UV 剔除
                    if (
                        polygon.u1 == 0f &&
                        polygon.u2 == 0f &&
                        polygon.v1 == 0f &&
                        polygon.v2 == 0f
                    ) continue
                    val normal = cube.buildLocalTransformMatrix().transformDirection(polygon.normal)
                    for (vertex in polygon.vertexes) {
                        val pos = cube.buildLocalTransformMatrix().transformPosition(Vector3f(vertex.x, vertex.y, vertex.z))
                        // 烘焙时 overlay/light 传 0（与 SimpleBedrockModel 参考实现一致）：
                        // 光照/叠加/颜色是每帧动态数据，由下方 mesh.write() 的 varying 提供，
                        // 不能固化进缓存的 mesh 顶点，否则首次渲染时的亮度会被永久缓存（如淡入阶段的满亮度）
                        meshBuilder.addVertex(
                            pos.x, pos.y, pos.z, 0xFFFFFFFF.toInt(),
                            vertex.u, vertex.v,
                            0, 0,
                            normal.x, normal.y, normal.z
                        )
                    }
                }
                culledMeshCollector.flush()
                return@computeIfAbsent AcceleratedEntityRenderingFeature
                    .getMeshType()
                    .builder
                    .build(culledMeshCollector)
            }.write(
                extension,
                color,
                light,
                overlay
            )
        extension.endTransform()
    }
}