package cn.solarmoon.spark_core.visual_effect.shape

import cn.solarmoon.spark_core.physics.lerp
import cn.solarmoon.spark_core.physics.level.ClientPhysicsLevel
import cn.solarmoon.spark_core.physics.level.PhysicsLevel
import cn.solarmoon.spark_core.physics.toMatrix4f
import cn.solarmoon.spark_core.physics.visualizer.ShapeVisualizerRegistry
import cn.solarmoon.spark_core.visual_effect.VisualEffectRenderer
import com.jme3.bullet.collision.PhysicsCollisionObject
import com.jme3.bullet.collision.shapes.CompoundCollisionShape
import com.jme3.bullet.objects.PhysicsRigidBody
import com.jme3.math.Vector3f
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.MultiBufferSource
import net.minecraft.world.phys.Vec3
import org.joml.Matrix4f

class ShapeRenderer : VisualEffectRenderer() {

    override fun tick() {

    }

    override fun physTick(physLevel: PhysicsLevel) {

    }

    /**
     * 渲染物理碰撞形状的可视化效果
     * 
     * @param mc Minecraft实例，用于获取游戏环境信息
     * @param camPos 摄像机位置向量，用于计算相对渲染位置
     * @param poseStack 姿态矩阵栈，用于管理3D变换状态
     * @param bufferSource 缓冲区源，用于获取渲染所需的顶点缓冲区
     * @param partialTicks 部分刻度值，用于平滑动画过渡
     * 
     * 该方法通过以下步骤完成渲染：
     * 1. 检查当前维度和渲染设置有效性
     * 2. 遍历所有物理对象并筛选可渲染对象
     * 3. 处理复合形状的层次化渲染
     * 4. 使用注册的可视化器进行最终形状绘制
     */
    override fun render(
        mc: Minecraft,
        camPos: Vec3,
        poseStack: PoseStack,
        bufferSource: MultiBufferSource,
        partialTicks: Float
    ) {
        val level = mc.level ?: return
        if (!mc.entityRenderDispatcher.shouldRenderHitBoxes()) return
        /**
         * 获取客户端物理层级并遍历所有物理对象
         * 过滤条件：仅渲染碰撞组不为0或属于方块碰撞组的对象
         */
        val physLevel = level.physicsLevel as ClientPhysicsLevel
        physLevel.world.pcoList.forEach { body ->
            if (body.collideWithGroups == 0 && body.collisionGroup!=PhysicsCollisionObject.COLLISION_GROUP_BLOCK) return@forEach
            val shape = body.collisionShape
            val transform = if (body is PhysicsRigidBody && !body.isKinematic) {
                body.lastTickTransform.lerp(body.tickTransform, partialTicks).toTransformMatrix().toMatrix4f()
            } else {
                body.tickTransform.lerp(body.getTransform(null), partialTicks).toTransformMatrix().toMatrix4f()
            }
            /**
             * 处理复合形状的子元素渲染
             * 对每个子元素应用独立变换矩阵并递归调用渲染
             */
            if (shape is CompoundCollisionShape) {
                shape.listChildren().forEach {
                    val visualizer = ShapeVisualizerRegistry.getVisualizer(it.shape) ?: return@forEach
                    val childTransform = it.copyTransform(null).toTransformMatrix().toMatrix4f()
                    val finalMatrix = transform.mul(childTransform, Matrix4f())
                    visualizer.render(
                        physLevel,
                        body,
                        finalMatrix,
                        it.shape,
                        mc,
                        camPos,
                        poseStack,
                        bufferSource,
                        partialTicks
                    )
                }
            } else {
                /**
                 * 单独形状的直接渲染流程
                 * 使用注册的可视化器执行最终绘制操作
                 */
                val visualizer = ShapeVisualizerRegistry.getVisualizer(shape) ?: return@forEach
                visualizer.render(physLevel, body, transform, shape, mc, camPos, poseStack, bufferSource, partialTicks)
            }
        }
    }

}