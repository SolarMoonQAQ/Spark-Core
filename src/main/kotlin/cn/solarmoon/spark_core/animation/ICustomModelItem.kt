package cn.solarmoon.spark_core.animation

import cn.solarmoon.spark_core.animation.anim.play.ModelIndex
import cn.solarmoon.spark_core.registry.common.SparkDataComponents
import net.minecraft.world.item.ItemDisplayContext
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.Level
import org.joml.Vector3f
import java.awt.Color

/**
 * 使物品类继承此接口以实现基岩版自定义物品模型
 * Implement this interface to customize the bedrock edition model, texture, and animation of Item classes
 * 还需在SparkCustomModelItem类中注册相应物品的自定义渲染器
 * Register the custom renderer for the corresponding item in the SparkCustomModelItem class
 */
interface ICustomModelItem {
    /**
     *
     * 获取存储有模型、贴图、动画等内容的动画体，用于物品渲染
     *
     * Get an instance of ItemAnimatable that contains the model, texture, and animation content, for item rendering
     *
     * @param itemStack
     *
     *物品堆栈，同类不同物品堆实例可拥有不同的动画体进而拥有不同的模型动画效果
     *
     *Item stack, different instances of the same item can have different ItemAnimatable instances to have different model animation effects
     * @param context
     *
     *此物品被渲染的场景，如GUI、凋落物、第一人称视角等
     *
     *The scene where this item is rendered, such as GUI, falling blocks, first-person view
     * @return 动画体对象 ItemAnimatable instance
     */
    fun getRenderInstance(itemStack: ItemStack, level: Level, context: ItemDisplayContext): ItemAnimatable? {
        try {
            var customModels: java.util.HashMap<ItemDisplayContext, ItemAnimatable>? =
                itemStack.get(SparkDataComponents.CUSTOM_ITEM_MODEL)
            if (customModels == null) customModels = HashMap()
            var animatable = customModels[context]
            //为什么会出现itemStack不匹配的情况？
            if (animatable == null || animatable.itemStack != itemStack || animatable.animLevel !== level) animatable =
                createItemAnimatable(itemStack, level, context)
            return animatable
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * <p>指定不同情况下自定义模型物品的模型贴图动画资源路径</p>
     * <p>Specify the model, texture, and animation resources location for custom models of different items in different scenarios</p>
     */
    fun getModelIndex(itemStack: ItemStack, level: Level, context: ItemDisplayContext): ModelIndex

    fun createItemAnimatable(itemStack: ItemStack, level: Level, context: ItemDisplayContext): ItemAnimatable? {
        val customModels = if (itemStack.has(SparkDataComponents.CUSTOM_ITEM_MODEL))
            itemStack.get(SparkDataComponents.CUSTOM_ITEM_MODEL)!!
        else HashMap()
        val modelIndex = getModelIndex(itemStack, level, context)
        val animatable = ItemAnimatable(itemStack, level)
        animatable.modelIndex = modelIndex
        customModels[context] = animatable
        itemStack.set(SparkDataComponents.CUSTOM_ITEM_MODEL, customModels)
        return animatable
    }

    /**
     *
     * 是否在特定情况下使用2D模型，例如物品栏、掉落物等场景
     *
     * Whether to use 2D model in specific scenarios, such as inventory, drops
     *
     * @return
     *
     *true: 使用2D模型，false: 不使用2D模型
     *
     *true: use 2D model, false: not use 2D model
     */
    fun use2dModel(itemStack: ItemStack, level: Level, displayContext: ItemDisplayContext): Boolean {
        return true
    }

    fun getRenderOffset(itemStack: ItemStack, level: Level, displayContext: ItemDisplayContext): Vector3f {
        return Vector3f(0.0f, 0.0f, 0.0f)
    }

    fun getRenderRotation(itemStack: ItemStack, level: Level, displayContext: ItemDisplayContext): Vector3f {
        return Vector3f(0.0f, 0.0f, 0.0f)
    }

    fun getRenderScale(itemStack: ItemStack, level: Level, displayContext: ItemDisplayContext): Vector3f {
        return Vector3f(1.0f, 1.0f, 1.0f)
    }

    fun getColor(itemStack: ItemStack, level: Level, displayContext: ItemDisplayContext): Color {
        return Color.WHITE
    }
}