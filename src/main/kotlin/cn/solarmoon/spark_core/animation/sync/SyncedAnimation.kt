package cn.solarmoon.spark_core.animation.sync

import cn.solarmoon.spark_core.animation.IAnimatable
import cn.solarmoon.spark_core.animation.anim.play.AnimModificationData
import cn.solarmoon.spark_core.animation.anim.play.MixedAnimation
import net.minecraft.server.level.ServerPlayer
import net.neoforged.neoforge.network.PacketDistributor

/**
 * 可同步动画，将动画和指令包装入此类中，使用[syncToClient]或[syncToClientExceptPresentPlayer]即可使用极少的网络数据轻松同步动画到客户端
 *
 * 此包装动画必须是唯一且双端一致的，因此一定只能在静态层构造
 *
 * 它的优势在于发送数据极少，同步较为及时，但是必须以静态函数进行构造并在**mod加载时**进行注册（加载），因此灵活性欠缺，无法作为内部参数动态修改动画数据，并且多写几行相对而言也不方便
 *
 * 如果追求灵活性，可以使用[IAnimatable.syncAnimDataToClient]方法进行直接同步，对于中低负载的同步情况也是比较即时的
 *
 * ## 同时要说明的是，本模组的动画同步机制基本分为三种：
 * 1. 如果动画是由本地客户端执行的某个操作播放的（如奔跑/攻击等），那么会先在客户端立刻播放动画，同时立刻发送对应数据到服务端，再在服务端同步动画到除了该客户端玩家以外的玩家端上
 * 2. 而如果动画是由非客户端的情况产生的，如某个生物在服务端的动画，则由该生物从服务端发送动画到所有人的客户端中
 * 3. 而如果动画是根据一些双端本就一致的条件播放的，如“player.name == '233'”，此时无需进行同步，只要满足条件时直接播放动画即可
 */
class SyncedAnimation(
    val id: Int,
    private val _anim: MixedAnimation
) {
    /**
     * 此构造会自动分配动画id，因此最好不要自己设定以免冲突
     */
    constructor(anim: MixedAnimation) : this(nextId(), anim)

    val anim get() = _anim.copy()
        
    init {
        ALL_CONSUME_ANIMATIONS.put(id, this)
    }

    var command: ((IAnimatable<*>) -> Unit)? = null

    /**
     * @param modifier 可以对动画部分数据进行额外修改
     */
    fun consume(animatable: IAnimatable<*>, modifier: AnimModificationData = AnimModificationData()) {
        command?.invoke(animatable) ?: run {
            animatable.animController.stopAndAddAnimation(anim.apply {
                modelPath = animatable.animData.modelPath
                modifier.speed.takeIf { it != -1f }?.let { speed = it }
                modifier.startTransSpeed.takeIf { it != -1f }?.let { startTransSpeed = it }
                modifier.tick.takeIf { it != -1f }?.let { tick = it.toDouble() }
            })
        }
    }

    fun isPlaying(animatable: IAnimatable<*>, level: Int = 0, filter: (MixedAnimation) -> Boolean = { true }): Boolean {
        return animatable.animController.isPlaying(anim.name, level, filter)
    }

    /**
     * 如果动画是由非玩家的生物所播放的，使用此方法进行同步，将会从服务端同步动画到所有客户端玩家
     * @param modifier 可以对动画进行额外修改
     */
    fun syncToClient(entityId: Int, modifier: AnimModificationData = AnimModificationData()) {
        PacketDistributor.sendToAllPlayers(SyncedAnimPayload(entityId, id, modifier))
    }

    /**
     * 如果动画由当前正在操作的玩家执行，并且此操作还是由本地玩家发送到服务端的操作，那么使用此方式同步，将把动画同步到除了该玩家以外的玩家电脑上
     *
     * 它的总体思路是，如果操作是客户端的操作，直接在客户端执行动画，然后发送到服务端，由服务端同步动画到别的玩家电脑，这样可以增加本地操作的流畅度
     * @param modifier 可以对动画进行额外修改
     */
    fun syncToClientExceptPresentPlayer(player: ServerPlayer, modifier: AnimModificationData = AnimModificationData()) {
        PacketDistributor.sendToPlayersNear(player.serverLevel(), player, player.x, player.y, player.z, 512.0, SyncedAnimPayload(player.id, id, modifier))
    }

    companion object {
        @JvmStatic
        val ALL_CONSUME_ANIMATIONS: MutableMap<Int, SyncedAnimation> = mutableMapOf()

        @JvmStatic
        val STOP = SyncedAnimation(MixedAnimation("")).apply { command = { it.animController.stopAllAnimation() } }

        @JvmStatic
        fun registerAnim() {}

        private var currentId: Int = 0

        private fun nextId(): Int {
            while (ALL_CONSUME_ANIMATIONS.containsKey(currentId)) {
                currentId++
            }
            return currentId
        }
    }

}
