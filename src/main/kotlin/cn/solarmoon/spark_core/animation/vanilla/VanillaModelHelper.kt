package cn.solarmoon.spark_core.animation.vanilla

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimData
import net.minecraft.client.Minecraft
import net.minecraft.client.model.HumanoidModel
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
    fun shouldSwitchToAnim(animatable: IAnimatable<*>) = !animatable.animController.isPlaying(null)

    @JvmStatic
    fun setRoot(child: ModelPart, root: ModelPart) {
        (child as ITransformModelPart).root = root
    }

    @JvmStatic
    fun isHumanoidModel(entity: Entity): Boolean {
        val renderer = Minecraft.getInstance().entityRenderDispatcher.getRenderer(entity)
        return renderer is LivingEntityRenderer<*, *> && renderer.model is HumanoidModel<*>
    }

    @JvmStatic
    fun setPivot(animData: AnimData, boneName: String, model: ModelPart) {
        (model as ITransformModelPart).pivot.set(animData.model.getBone(boneName).pivot.toVector3f())
    }

    @JvmStatic
    fun setPivot(pivot: Vector3f, model: ModelPart) {
        (model as ITransformModelPart).pivot.set(pivot)
    }

    @JvmStatic
    fun applyTransform(animData: AnimData, boneName: String, part: ModelPart, partialTicks: Float) {
        if (part !is ITransformModelPart) return
        val pos = animData.playData.getMixedBoneAnimPosition(boneName, partialTicks).mul(16f).apply { x = -x; y = -y }
        val rot = animData.playData.getMixedBoneAnimRotation(boneName, partialTicks).apply { x = -x; y = -y }
        val scale = animData.playData.getMixedBoneAnimScale(boneName, partialTicks)
        part.offsetPos(pos)
        if (boneName == "head") part.offsetRotation(rot) else part.setRotation(rot.x, rot.y, rot.z)
        part.xScale = scale.x; part.yScale = scale.y; part.zScale = scale.z
    }

}