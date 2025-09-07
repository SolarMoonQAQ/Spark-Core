package cn.solarmoon.spark_core.animation.vanilla

import cn.solarmoon.spark_core.animation.IAnimatable
import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
import net.minecraft.client.model.IllagerModel
import net.minecraft.client.model.geom.ModelPart
import net.minecraft.client.renderer.entity.LivingEntityRenderer
import net.minecraft.world.entity.Entity
import net.neoforged.api.distmarker.Dist
import net.neoforged.api.distmarker.OnlyIn
import org.joml.Vector3f

@OnlyIn(Dist.CLIENT)
object VanillaModelHelper {

    /**
     * 是否应当以自定义动画覆盖原版的动画模型参数
     * @return 正在播放默认的任意自定义动画则为true
     */
    @JvmStatic
    fun shouldSwitchToAnim(animatable: IAnimatable<*>) = animatable.animController.isPlayingAnim

    @JvmStatic
    fun setRoot(child: ModelPart, root: ModelPart) {
        (child as ITransformModelPart).root = root
    }

    @JvmStatic
    fun isHumanoidModel(entity: Entity): Boolean {
        val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(entity)
        return renderer is LivingEntityRenderer<*, *> && (renderer.model is HumanoidModel<*> || renderer.model is IllagerModel<*>)
    }

    @JvmStatic
    fun setPivot(animatable: IAnimatable<*>, boneName: String, model: ModelPart) {
        animatable.modelController.model?.origin?.getBone(boneName)?.pivot?.toVector3f()?.let {
            (model as ITransformModelPart).pivot.set(it)
        }
    }

    @JvmStatic
    fun setPivot(pivot: Vector3f, model: ModelPart) {
        (model as ITransformModelPart).pivot.set(pivot)
    }

    @JvmStatic
    fun applyTransform(animatable: IAnimatable<*>, boneName: String, part: ModelPart, partialTicks: Float) {
        if (part !is ITransformModelPart) return
        val bone = animatable.modelController.model?.getBonePose(boneName) ?: return
        val pos = bone.getLocalPosition(partialTicks).toVector3f().mul(16f).apply { x = -x; y = -y }
        val rot = bone.getLocalRotation(partialTicks).toVector3f().apply { x = -x; y = -y }
        val scale = bone.getLocalScale(partialTicks).toVector3f()
        part.offsetPos(pos)
        part.setRotation(rot.x, rot.y, rot.z)
        part.xScale = scale.x; part.yScale = scale.y; part.zScale = scale.z
    }

}