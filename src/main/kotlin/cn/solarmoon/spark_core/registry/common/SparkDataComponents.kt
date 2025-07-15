package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.ItemAnimatable
import com.mojang.serialization.Codec
import net.minecraft.world.item.ItemDisplayContext
import java.util.HashMap

object SparkDataComponents {
    @JvmStatic
    fun register() {}

    @JvmStatic
    val CUSTOM_ITEM_MODEL = SparkCore.REGISTER.dataComponent<HashMap<ItemDisplayContext, ItemAnimatable>>()
        .id("custom_item_model")
        .build {
            it
                .persistent(Codec.unit(HashMap( 6)))
                .cacheEncoding()
        }
}