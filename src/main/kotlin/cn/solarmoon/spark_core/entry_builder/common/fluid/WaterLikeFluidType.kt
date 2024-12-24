package cn.solarmoon.spark_core.fluid

import net.minecraft.client.Minecraft
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundEvents
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions
import net.neoforged.neoforge.common.SoundActions


class WaterLikeFluidType(val defaultUnderOverlay: Boolean, modId: String, id: String, color: Int, properties: Properties): BaseFluidType(modId, id, color, properties) {//

    companion object {
        @JvmStatic
        fun waterLikeProperties(canConvertToSource: Boolean): Properties {
            return Properties.create()
                .fallDistanceModifier(0f)
                .canExtinguish(true)
                .canConvertToSource(canConvertToSource)
                .supportsBoating(true)
                .sound(SoundActions.BUCKET_FILL, SoundEvents.BUCKET_FILL)
                .sound(SoundActions.BUCKET_EMPTY, SoundEvents.BUCKET_EMPTY)
                .sound(SoundActions.FLUID_VAPORIZE, SoundEvents.FIRE_EXTINGUISH)
        }
    }

    override fun getClientExtension(): IClientFluidTypeExtensions {
        return object : IClientFluidTypeExtensions {
            private val UNDERWATER_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/underwater.png")
            private val WATER_STILL = ResourceLocation.withDefaultNamespace("block/water_still")
            private val WATER_FLOW = ResourceLocation.withDefaultNamespace("block/water_flow")
            private val WATER_OVERLAY = ResourceLocation.withDefaultNamespace("block/water_overlay")

            override fun getStillTexture(): ResourceLocation {
                return WATER_STILL
            }

            override fun getFlowingTexture(): ResourceLocation {
                return WATER_FLOW
            }

            override fun getOverlayTexture(): ResourceLocation? {
                return WATER_OVERLAY
            }

            override fun getRenderOverlayTexture(mc: Minecraft): ResourceLocation? {
                return if (defaultUnderOverlay) UNDERWATER_LOCATION else spriteUnder
            }

            override fun getTintColor(): Int {
                return color
            }
        }
    }

}