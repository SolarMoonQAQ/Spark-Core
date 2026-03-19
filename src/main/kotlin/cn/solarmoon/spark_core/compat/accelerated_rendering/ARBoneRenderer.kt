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

object ARBoneRenderer : IAcceleratedRenderer<OBone> {

    val cubeMeshes: MutableMap<OBone, MutableMap<IBufferGraph, IMesh>> = Reference2ObjectOpenHashMap()

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
        extension.beginTransform(transform, normal)
        cubeMeshes
            .computeIfAbsent(bone) { mutableMapOf() }
            .computeIfAbsent(extension) {
                val culledMeshCollector = CulledMeshCollector(extension)
                val meshBuilder: VertexConsumer = extension.decorate(culledMeshCollector)

                //TODO: 将整个骨骼的Cube和Mesh作为IMesh存储，进一步提升性能

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