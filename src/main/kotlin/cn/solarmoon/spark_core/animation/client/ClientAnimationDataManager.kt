package cn.solarmoon.spark_core.animation.client

import cn.solarmoon.spark_core.SparkCore
import cn.solarmoon.spark_core.animation.anim.origin.OAnimationSet
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import net.minecraft.resources.ResourceLocation
import java.util.concurrent.ConcurrentHashMap

// 假设 OAnimationSet 是代表动画集数据结构的类
// 在 OAnimationSet.kt 文件可用后，需要取消注释并确保导入正确

/**
 * 客户端动画数据管理器，用于存储和管理从服务器同步过来的动画数据。
 */
object ClientAnimationDataManager {
    private val animationData: MutableMap<ResourceLocation, OAnimationSet > = ConcurrentHashMap()
    private val gson = Gson()

    /**
     * 获取指定位置的动画集。
     *
     * @param location 动画的ResourceLocation
     * @return OAnimationSet? 如果找到则返回动画集，否则返回null
     */
    fun getAnimationSet(location: ResourceLocation): OAnimationSet?  {
        return animationData[location]
    }


    /**
     * 更新或添加一个动画集 (直接通过对象)。
     *
     * @param location 动画的ResourceLocation
     * @param animationSet OAnimationSet 对象
     */
    fun updateAnimationSet(location: ResourceLocation, animationSet: OAnimationSet) {
        animationData[location] = animationSet
        SparkCore.LOGGER.debug("Updated animation set for: {} with OAnimationSet object", location)
    }

    /**
     * 移除一个动画集。
     *
     * @param location 动画的ResourceLocation
     */
    fun removeAnimationSet(location: ResourceLocation) {
        if (animationData.remove(location) != null) {
            SparkCore.LOGGER.debug("Removed animation set for: {}", location)
        }
    }


    /**
     * 替换所有动画集 (直接通过 OAnimationSet 对象 Map)。
     * 此方法会清空现有的所有动画数据，并用提供的数据集进行填充。
     *
     * @param newAnimationSets 一个Map，键是动画的ResourceLocation，值是 OAnimationSet 对象。
     */
    fun replaceAllAnimationSetsFromObjects(newAnimationSets: Map<ResourceLocation, OAnimationSet>) {
        animationData.clear()
        SparkCore.LOGGER.info("Cleared all client animation data. Receiving new batch (OAnimationSet objects)... ")
        newAnimationSets.forEach { (location, oAnimationSet) ->
            // 直接将对象放入 map
            animationData[location] = oAnimationSet
            SparkCore.LOGGER.debug("Added/Replaced animation set for: {} with OAnimationSet object", location)
        }
        SparkCore.LOGGER.info("Finished processing batch of ${newAnimationSets.size} animation sets (OAnimationSet objects).")
    }
}
