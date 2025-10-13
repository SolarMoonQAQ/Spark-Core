package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.ItemAnimatable
import cn.solarmoon.spark_core.entry_builder.dataComponentBuilder
import com.mojang.serialization.Codec
import net.minecraft.world.item.ItemDisplayContext
import java.util.HashMap

object SparkDataComponents {
    @JvmStatic
    fun register() {}

    val CUSTOM_ITEM_MODEL = SparkCore.REGISTER.dataComponentType<HashMap<ItemDisplayContext, ItemAnimatable>> {
        id = "custom_item_model"
        factory = dataComponentBuilder {
            persistent(Codec.unit(HashMap<ItemDisplayContext, ItemAnimatable>(6)))
                .cacheEncoding()
        }
    }

}