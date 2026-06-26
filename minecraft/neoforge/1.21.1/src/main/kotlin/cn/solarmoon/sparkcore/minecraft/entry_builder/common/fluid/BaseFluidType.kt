package cn.solarmoon.spark_core.fluid

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
import net.neoforged.neoforge.fluids.FluidType

/**
 * 基本fluidType类，设定了默认液体所需贴图路径和颜色
 */
open class BaseFluidType(modId: String, id: String, val color: Int, properties: Properties): FluidType(properties) {//

    protected var spriteStill = ResourceLocation.tryBuild(modId, "block/fluid/" + id + "_still")
    protected var spriteFlowing = ResourceLocation.tryBuild(modId, "block/fluid/" + id + "_flow")
    protected var spriteOverlay = ResourceLocation.tryBuild(modId, "block/fluid/" + id + "_overlay.png")
    protected var spriteUnder = ResourceLocation.tryBuild(modId, "textures/misc/" + id + "_under.png")

    open fun getClientExtension(): IClientFluidTypeExtensions {
        return object : IClientFluidTypeExtensions {
            override fun getStillTexture(): ResourceLocation {
                return spriteStill!!
            }

            override fun getFlowingTexture(): ResourceLocation {
                return spriteFlowing!!
            }

            override fun getOverlayTexture(): ResourceLocation? {
                return spriteOverlay
            }

            override fun getRenderOverlayTexture(mc: Minecraft): ResourceLocation? {
                return spriteUnder
            }

            override fun getTintColor(): Int {
                return color
            }
        }
    }

}