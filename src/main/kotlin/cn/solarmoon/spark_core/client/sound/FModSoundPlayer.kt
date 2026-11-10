package cn.solarmoon.spark_core.client.sound

import cn.solarmoon.spark_core.pack.modules.FModModule
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import org.lwjgl.fmod.FMOD.FMOD_OK
import org.lwjgl.fmod.FMODStudio.*
import org.lwjgl.fmod.FMOD_STUDIO_PARAMETER_DESCRIPTION
import org.lwjgl.system.MemoryStack
import java.util.UUID

/** 客户端 FMOD 播放入口。事件在听者原位播放，播放和控制方法须在客户端主线程调用。 */
object FModSoundPlayer {
    private class Voice(
        val handle: Long,
        val description: Long,
        val level: ClientLevel,
        val category: SoundSource
    ) {
        var paused = false
        var gain = Float.NaN
        var fading = false
        val writableParameters = mutableMapOf<String, Boolean>()
    }

    private val voices = mutableMapOf<UUID, Voice>()

    /** 查询指定 bank 是否已加载。实际播放是否可用以 [tryPlay] 的结果为准。 */
    @JvmStatic
    fun isBankLoaded(bankId: ResourceLocation): Boolean = FModModule.isBankLoaded(bankId)

    /**
     * 在指定 bank 中查找事件，设置初始参数后播放，并返回本次播放的 UUID。
     * 资源缺失、初始参数无效或启动失败时返回空，调用方可播放备用音效。
     * 返回 UUID 表示 Studio 已接受启动命令，实际出声由 Studio 异步处理。
     * 循环由 Studio 事件定义，[category] 对应 Minecraft 的音量选项。
     */
    @JvmStatic
    @JvmOverloads
    fun tryPlay(
        bankId: ResourceLocation,
        eventPath: String,
        initialParams: Map<String, Float> = emptyMap(),
        category: SoundSource = SoundSource.NEUTRAL
    ): UUID? {
        val client = Minecraft.getInstance()
        if (!client.isSameThread || !eventPath.startsWith("event:/") || '\u0000' in eventPath) return null
        val level = client.level ?: return null
        return synchronized(FModModule) {
            val bank = FModModule.loadedBanks[bankId] ?: return@synchronized null
            MemoryStack.stackPush().use { stack ->
                val pointer = stack.mallocPointer(1)
                val description = findEvent(FModModule.studio, bank, eventPath, stack) ?: return@use null
                if (FMOD_Studio_EventDescription_CreateInstance(description, pointer) != FMOD_OK) return@use null
                val voice = Voice(pointer[0], description, level, category)
                var started = false
                try {
                    if (!syncOptions(voice, client) ||
                        !initialParams.all { (name, value) -> writeParameter(voice, name, value) } ||
                        FMOD_Studio_EventInstance_Start(voice.handle) != FMOD_OK
                    ) return@use null

                    started = true
                    UUID.randomUUID().also { voices[it] = voice }
                } finally {
                    // 启动成功才交给帧更新维护，否则在这里归还刚创建的实例。
                    if (!started) FMOD_Studio_EventInstance_Release(voice.handle)
                }
            }
        }
    }

    /** 设置指定播放实例的局部参数。实例已结束或参数不可写时返回 false。 */
    @JvmStatic
    fun setParameter(uuid: UUID, name: String, value: Float): Boolean =
        control(uuid) { !it.fading && writeParameter(it, name, value) }

    /** 按 Studio 工程定义的停止过渡淡出。 */
    @JvmStatic
    fun fadeOut(uuid: UUID): Boolean = control(uuid) {
        if (it.fading) return@control true
        it.fading = FMOD_Studio_EventInstance_Stop(it.handle, FMOD_STUDIO_STOP_ALLOWFADEOUT) == FMOD_OK
        it.fading
    }

    /** 立即停止并释放指定实例。 */
    @JvmStatic
    fun stop(uuid: UUID): Boolean = control(uuid) {
        voices.remove(uuid)
        stopAndRelease(it)
    }

    /** Studio 释放时废弃旧 UUID。bank 模块在持锁时调用。 */
    internal fun onStudioReleased() {
        voices.clear()
    }

    /** 应用游戏音量和暂停状态，并在离开世界或自然播放结束时释放实例。 */
    @SubscribeEvent
    fun onFrame(event: RenderFrameEvent.Post) {
        val client = Minecraft.getInstance()
        synchronized(FModModule) {
            val studio = FModModule.studio
            if (studio == 0L) return
            MemoryStack.stackPush().use { stack ->
                val state = stack.mallocInt(1)
                val iterator = voices.values.iterator()
                while (iterator.hasNext()) {
                    val voice = iterator.next()
                    if (voice.level !== client.level || !syncOptions(voice, client)) {
                        iterator.remove()
                        stopAndRelease(voice)
                        continue
                    }
                    if (FMOD_Studio_EventInstance_GetPlaybackState(voice.handle, state) != FMOD_OK ||
                        state[0] == FMOD_STUDIO_PLAYBACK_STOPPED
                    ) {
                        iterator.remove()
                        FMOD_Studio_EventInstance_Release(voice.handle)
                    }
                }
                // 暂停和退出到菜单时也要提交停止与释放命令。
                val result = FMOD_Studio_System_Update(studio)
                if (result != FMOD_OK) FModModule.disablePlayback(result)
            }
        }
    }

    /** Studio 全局解析路径，再以 bank 的事件列表核对归属。 */
    private fun findEvent(studio: Long, bank: Long, path: String, stack: MemoryStack): Long? {
        val pointer = stack.mallocPointer(1)
        if (FMOD_Studio_System_GetEvent(studio, path, pointer) != FMOD_OK) return null
        val description = pointer[0]
        val count = stack.mallocInt(1)
        if (FMOD_Studio_Bank_GetEventCount(bank, count) != FMOD_OK || count[0] == 0) return null
        val events = stack.mallocPointer(count[0])
        if (FMOD_Studio_Bank_GetEventList(bank, events, count) != FMOD_OK) return null
        return description.takeIf { (0 until count[0]).any { events[it] == description } }
    }

    /** 仅控制当前世界内的实例，原生调用和 bank 重载共用同一把锁。 */
    private inline fun control(uuid: UUID, action: (Voice) -> Boolean): Boolean {
        val client = Minecraft.getInstance()
        if (!client.isSameThread) return false
        return synchronized(FModModule) {
            val voice = voices[uuid] ?: return@synchronized false
            voice.level === client.level && action(voice)
        }
    }

    /** 仅允许局部可写参数，防止一个 UUID 的更新影响其它实例。 */
    private fun writeParameter(voice: Voice, name: String, value: Float): Boolean {
        if (!value.isFinite() || name.isBlank() || '\u0000' in name) return false
        val writable = voice.writableParameters.getOrPut(name) {
            MemoryStack.stackPush().use { stack ->
                val parameter = FMOD_STUDIO_PARAMETER_DESCRIPTION.malloc(stack)
                val blockedFlags = FMOD_STUDIO_PARAMETER_READONLY or FMOD_STUDIO_PARAMETER_AUTOMATIC or
                    FMOD_STUDIO_PARAMETER_GLOBAL
                FMOD_Studio_EventDescription_GetParameterDescriptionByName(voice.description, name, parameter) == FMOD_OK &&
                    (parameter.flags() and blockedFlags) == 0
            }
        }
        return writable && FMOD_Studio_EventInstance_SetParameterByName(voice.handle, name, value, 0) == FMOD_OK
    }

    /** 仅在音量或暂停状态变化时提交对应命令。 */
    private fun syncOptions(voice: Voice, client: Minecraft): Boolean {
        val master = client.options.getSoundSourceVolume(SoundSource.MASTER)
        val gain = if (voice.category == SoundSource.MASTER) master
            else master * client.options.getSoundSourceVolume(voice.category)
        if (voice.gain != gain) {
            if (FMOD_Studio_EventInstance_SetVolume(voice.handle, gain) != FMOD_OK) return false
            voice.gain = gain
        }
        val paused = client.isPaused
        if (voice.paused != paused) {
            if (FMOD_Studio_EventInstance_SetPaused(voice.handle, if (paused) 1 else 0) != FMOD_OK) return false
            voice.paused = paused
        }
        return true
    }

    /** 先提交停止命令，再归还原生句柄。 */
    private fun stopAndRelease(voice: Voice): Boolean {
        val stopped = FMOD_Studio_EventInstance_Stop(voice.handle, FMOD_STUDIO_STOP_IMMEDIATE) == FMOD_OK
        val released = FMOD_Studio_EventInstance_Release(voice.handle) == FMOD_OK
        return stopped && released
    }
}
