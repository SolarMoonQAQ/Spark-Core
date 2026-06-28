package cn.solarmoon.spark_core.compat.accelerated_rendering

import cn.solarmoon.spark_core.animation.model.origin.OBone
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

/**
 * 骨骼级加速渲染器 —— 将整个骨骼的所有Cube和Mesh烘焙为单个IMesh，一次draw call完成渲染。
 *
 * 与OCube级别的ARCubeRenderer相比：
 * - ARCubeRenderer 每个cube一个mesh，每个cube一次 beginTransform/endTransform + write
 * - ARBoneRenderer  整个骨骼一个mesh，一次 beginTransform/endTransform + write
 * 对于含多个cube的骨骼，可大幅减少draw call数量。
 *
 * 注意：动态面剔除（背面剔除、小面剔除）交由AR渲染管线处理，不在mesh构建阶段执行。
 * 仅做静态UV剔除（uv全为零），与ARCubeRenderer行为一致。
 */
object ARBoneRenderer : IAcceleratedRenderer<OBone> {

    /** 骨骼级mesh缓存，key为(OBone, IBufferGraph) */
    val boneMeshes: MutableMap<OBone, MutableMap<IBufferGraph, IMesh>> = Reference2ObjectOpenHashMap()

    /** 临时变量，避免每帧新建 */
    private val tmpPos = Vector3f()
    private val tmpNormal = Vector3f()

    override fun render(
        vertexConsumer: VertexConsumer,
        bone: OBone,
        transform: Matrix4f,
        normal: Matrix3f,
        light: Int,
        overlay: Int,
        color: Int
    ) {
        val extension = VertexConsumerExtension.getAccelerated(vertexConsumer)
        // 骨骼的世界变换在渲染时通过beginTransform应用，mesh内部只存储骨骼局部空间的顶点
        extension.beginTransform(transform, normal)
        boneMeshes
            .computeIfAbsent(bone) { mutableMapOf() }
            .computeIfAbsent(extension) {
                val culledMeshCollector = CulledMeshCollector(extension)
                val meshBuilder: VertexConsumer = extension.decorate(culledMeshCollector)

                // 1. 烘焙所有Cube —— 应用cube的局部变换（pivot + rotation）
                for (cube in bone.cubes) {
                    val localTransform = cube.buildLocalTransformMatrix()
                    val localNormalMatrix = cube.buildLocalNormalMatrix()
                    for (i in cube.polygonSet.indices) {
                        val polygon = cube.polygonSet[i]
                        // 静态UV剔除：全部uv为0的面不渲染
                        if (polygon.u1 == 0f && polygon.u2 == 0f &&
                            polygon.v1 == 0f && polygon.v2 == 0f
                        ) continue
                        // 变换法线到骨骼局部空间
                        localNormalMatrix.transform(polygon.normal, tmpNormal)
                        for (vertex in polygon.vertexes) {
                            // 变换顶点位置到骨骼局部空间
                            localTransform.transformPosition(vertex.x, vertex.y, vertex.z, tmpPos)
                            meshBuilder.addVertex(
                                tmpPos.x, tmpPos.y, tmpPos.z, 0xFFFFFFFF.toInt(),
                                vertex.u, vertex.v,
                                overlay, light,
                                tmpNormal.x, tmpNormal.y, tmpNormal.z
                            )
                        }
                    }
                }

                // 2. 烘焙Mesh（OMesh）—— 顶点已在骨骼局部空间，无需额外变换
                bone.mesh?.let { mesh ->
                    for (polygon in mesh.polygons) {
                        for (vertex in polygon.vertices) {
                            meshBuilder.addVertex(
                                vertex.position.x.toFloat(), vertex.position.y.toFloat(), vertex.position.z.toFloat(),
                                0xFFFFFFFF.toInt(),
                                vertex.uv.x, vertex.uv.y,
                                overlay, light,
                                vertex.normal.x.toFloat(), vertex.normal.y.toFloat(), vertex.normal.z.toFloat()
                            )
                        }
                    }
                }

                culledMeshCollector.flush()
                return@computeIfAbsent AcceleratedEntityRenderingFeature
                    .getMeshType()
                    .builder
                    .build(culledMeshCollector)
            }.write(extension, color, light, overlay)
        extension.endTransform()
    }
}