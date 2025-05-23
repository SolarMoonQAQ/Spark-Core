package cn.solarmoon.spark_core.registry.common

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.play.TypedAnimation
import cn.solarmoon.spark_core.ik.component.TypedIKComponent
import cn.solarmoon.spark_core.sync.SyncData
import cn.solarmoon.spark_core.sync.SyncerType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.StreamCodec
import net.minecraft.world.item.Item
import net.minecraft.world.item.Rarity

object SparkRegistries {

    @JvmStatic
    val TYPED_ANIMATION = SparkCore.REGISTER.registry<TypedAnimation>()
        .id("typed_animation")
        .build { it.sync(true).create() }

    @JvmStatic
    val SYNCER_TYPE = SparkCore.REGISTER.registry<SyncerType>()
        .id("syncer_type")
        .build { it.sync(true).create() }

    @JvmStatic
    val SYNC_DATA_STREAM_CODEC = SparkCore.REGISTER.registry<StreamCodec<RegistryFriendlyByteBuf, out SyncData>>()
        .id("sync_data_stream_codec")
        .build { it.sync(true).create() }

    @JvmStatic
    val IK_COMPONENT_TYPE = SparkCore.REGISTER.registry<TypedIKComponent>()
        .id("ik_component_type") // Registry name
        .build { it.sync(true).create() } // Sync to client if needed

    @JvmStatic
    val MODEL_EDITOR_WAND = SparkCore.REGISTER.item<Item>() // Specify Item type explicitly
        .id("model_editor_wand") // 设置物品 ID
        .bound { Item(Item.Properties().rarity(Rarity.EPIC)) } // 提供 Item 实例的 Supplier
        .build() // 构建 DeferredHolder<Item, Item>

    @JvmStatic
    fun register() {}

}