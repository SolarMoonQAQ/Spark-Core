package cn.solarmoon.spark_core.local_control

import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.client.player.Input
import net.minecraft.client.player.LocalPlayer
import net.minecraft.network.protocol.common.custom.CustomPacketPayload
import net.neoforged.neoforge.client.event.InputEvent
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent
import kotlin.properties.Delegates

abstract class LocalInputController {

    val packages = ArrayDeque<CustomPacketPayload>()
    private var initChecker: Boolean by Delegates.observable(false) { _, old, new -> if (old != new && new) laterInit() }
    private val keyRecorder = hashMapOf<KeyMapping, Boolean>()
    private val keyPressTimer = hashMapOf<KeyMapping, Int>()
    private val tickingKeys = hashSetOf<KeyMapping>()

    abstract fun laterInit()

    abstract fun tick(player: LocalPlayer, input: Input)

    abstract fun onInteract(player: LocalPlayer, event: InputEvent.InteractionKeyMappingTriggered)

    abstract fun updateMovement(player: LocalPlayer, event: MovementInputUpdateEvent)

    fun lateInit() {
        initChecker = true
    }

    fun keyTick() {
        tickingKeys.forEach { key ->
            if (key.isDown) {
                keyPressTimer[key] = keyPressTimer.getOrPut(key) { 0 } + 1
            } else keyPressTimer[key] = 0

            keyRecorder[key] = key.isDown
        }
    }

    /**
     * **重要**
     *
     * 如果想要获取按键的按压时间等需要tick的操作，务必将按键在[laterInit]中使用此方法加入到tick列表中
     */
    fun addTickingKey(key: KeyMapping) {
        tickingKeys.add(key)
    }

    /**
     * 添加网络包以待在tick末尾一起整合发送
     */
    fun addPackage(pack: CustomPacketPayload) {
        packages.add(pack)
    }

    /**
     * 获取当前按键按下的tick总时长
     */
    fun getPressTick(key: KeyMapping): Int = keyPressTimer[key] ?: 0

    /**
     * 当该按键释放的一瞬间，将执行输入的指令[action]
     * @param key 监测的按键
     * @param action 其中Int值为该按键按下的总tick时长
     */
    fun onRelease(key: KeyMapping, action: (Int) -> Unit) {
        val isDownLast = keyRecorder.getOrDefault(key, false)
        val isDownNow = key.isDown
        if (isDownLast && !isDownNow) {
            action.invoke(getPressTick(key))
        }
    }

    /**
     * 当该按键按下时，将不断执行输入的指令[action]
     * @param key 监测的按键
     * @param action 其中Int值为该按键按下的总tick时长
     */
    fun onPress(key: KeyMapping, action: (Int) -> Unit) {
        if (key.isDown) {
            action.invoke(getPressTick(key))
        }
    }

    /**
     * 当该按键按下的一瞬间将执行输入的指令[action]
     * @param key 监测的按键
     * @param action 要执行的指令
     */
    fun onPressOnce(key: KeyMapping, action: () -> Unit) {
        val isDownLast = keyRecorder.getOrDefault(key, false)
        val isDownNow = key.isDown
        if (!isDownLast && isDownNow) {
            action.invoke()
        }
    }

}