package cn.solarmoon.spark_core.client.sound

import cn.solarmoon.spark_core.pack.modules.FModModule
import cn.solarmoon.spark_core.sound.ClientSpreadingSoundPlayer
import cn.solarmoon.spark_core.sound.ISoundSpreader
import net.minecraft.client.Minecraft
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.resources.ResourceLocation
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import net.minecraft.world.phys.Vec3
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.client.event.ClientTickEvent
import net.neoforged.neoforge.client.event.RenderFrameEvent
import org.lwjgl.fmod.FMOD.FMOD_OK
import org.lwjgl.fmod.FMODStudio.*
import org.lwjgl.fmod.FMOD_3D_ATTRIBUTES
import org.lwjgl.fmod.FMOD_STUDIO_PARAMETER_DESCRIPTION
import org.lwjgl.fmod.FMOD_VECTOR
import org.lwjgl.system.MemoryStack
import java.util.UUID

/** 客户端 FMOD 播放入口。2D 事件直接播放，绑定声源的 3D 事件跟随其位姿。 */
object FModSoundPlayer {
    private const val INTERIOR = "interior"

    private class Voice(
        val handle: Long,
        val description: Long,
        val level: ClientLevel,
        val category: SoundSource,
        val soundSpreader: ISoundSpreader?,
        val spatial: Boolean
    ) {
        var paused = false
        var gain = Float.NaN
        var fading = false
        var interior: Boolean? = null
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
     * 绑定 [soundSpreader] 后，播放器用实时听者判定维护可选的 interior 参数。
     * 只有绑定声源的 3D 事件才读取位置和速度。播放和控制方法须在客户端主线程调用。
     */
    @JvmStatic
    @JvmOverloads
    fun tryPlay(
        bankId: ResourceLocation,
        eventPath: String,
        initialParams: Map<String, Float> = emptyMap(),
        category: SoundSource = SoundSource.NEUTRAL,
        soundSpreader: ISoundSpreader? = null
    ): UUID? {
        val client = Minecraft.getInstance()
        if (!client.isSameThread || !eventPath.startsWith("event:/") || '\u0000' in eventPath) return null
        val level = client.level ?: return null
        return synchronized(FModModule) {
            val bank = FModModule.loadedBanks[bankId] ?: return@synchronized null
            val studio = FModModule.studio
            MemoryStack.stackPush().use { stack ->
                val pointer = stack.mallocPointer(1)
                val description = findEvent(studio, bank, eventPath, stack) ?: return@use null
                val spatial = if (soundSpreader == null) false else {
                    val is3D = stack.mallocInt(1)
                    if (FMOD_Studio_EventDescription_Is3D(description, is3D) != FMOD_OK) return@use null
                    is3D[0] != 0
                }
                if (FMOD_Studio_EventDescription_CreateInstance(description, pointer) != FMOD_OK) return@use null
                val voice = Voice(pointer[0], description, level, category, soundSpreader, spatial)
                val uuid = UUID.randomUUID()
                var started = false
                try {
                    if (!syncOptions(voice, client)) return@use null
                    val parametersReady = initialParams.all { (name, value) ->
                        usesListenerParameter(voice, name) || writeParameter(voice, name, value)
                    }
                    if (!parametersReady) return@use null

                    // 先写车内状态，再启动事件，避免第一帧用到 bank 的默认混音。
                    updateInterior(voice, client.cameraEntity, client.options.cameraType.isFirstPerson)
                    if (spatial) {
                        val attributes = FMOD_3D_ATTRIBUTES.malloc(stack)
                        val origin = updateListener(studio, client, attributes) ?: return@use null
                        if (!updateSource(uuid, voice, origin, attributes)) return@use null
                    }
                    if (FMOD_Studio_EventInstance_Start(voice.handle) != FMOD_OK) return@use null

                    started = true
                    voices[uuid] = voice
                    uuid
                } finally {
                    // 启动成功才交给帧更新维护，否则在这里归还刚创建的实例。
                    if (!started) FMOD_Studio_EventInstance_Release(voice.handle)
                }
            }
        }
    }

    /** 设置实例的业务参数。已结束、不可写或由听者维护的参数返回 false。 */
    @JvmStatic
    fun setParameter(uuid: UUID, name: String, value: Float): Boolean =
        control(uuid) { !it.fading && !usesListenerParameter(it, name) && writeParameter(it, name, value) }

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

    /** 客户端 tick 结束后取一次当前相机实体和视角，供本世界的活跃声源共用。 */
    @SubscribeEvent
    fun onClientTick(event: ClientTickEvent.Post) {
        val client = Minecraft.getInstance()
        val level = client.level ?: return
        val cameraEntity = client.cameraEntity
        val firstPerson = client.options.cameraType.isFirstPerson
        synchronized(FModModule) {
            for (voice in voices.values) {
                if (voice.level === level && !voice.fading) updateInterior(voice, cameraEntity, firstPerson)
            }
        }
    }

    /** 在同一帧提交听者和全部 3D 声源的位姿，并清理已经结束的实例。 */
    @SubscribeEvent
    fun onFrame(event: RenderFrameEvent.Post) {
        val client = Minecraft.getInstance()
        synchronized(FModModule) {
            val studio = FModModule.studio
            if (studio == 0L) return
            MemoryStack.stackPush().use { stack ->
                val state = stack.mallocInt(1)
                val level = client.level
                val attributes = if (level != null && voices.values.any { it.spatial && it.level === level }) {
                    FMOD_3D_ATTRIBUTES.malloc(stack)
                } else null
                val origin = attributes?.let { updateListener(studio, client, it) }
                val iterator = voices.entries.iterator()
                while (iterator.hasNext()) {
                    val (uuid, voice) = iterator.next()
                    if (voice.level !== level || !syncOptions(voice, client)) {
                        iterator.remove()
                        stopAndRelease(voice)
                        continue
                    }
                    if (FMOD_Studio_EventInstance_GetPlaybackState(voice.handle, state) != FMOD_OK ||
                        state[0] == FMOD_STUDIO_PLAYBACK_STOPPED
                    ) {
                        iterator.remove()
                        FMOD_Studio_EventInstance_Release(voice.handle)
                    } else if (voice.spatial && origin != null && attributes != null) {
                        updateSource(uuid, voice, origin, attributes)
                    }
                }
                // 暂停和退出到菜单时也要提交停止与释放命令。
                val result = FMOD_Studio_System_Update(studio)
                if (result != FMOD_OK) FModModule.disablePlayback(result)
            }
        }
    }

    /** 提交听者朝向和世界速度，返回本帧共享的相机原点。 */
    private fun updateListener(studio: Long, client: Minecraft, attributes: FMOD_3D_ATTRIBUTES): Vec3? {
        val camera = client.gameRenderer.mainCamera
        val cameraEntity = client.cameraEntity
        val origin = if (camera.isInitialized) camera.position else cameraEntity?.eyePosition ?: return null
        val velocity = if (client.isPaused) Vec3.ZERO else ClientSpreadingSoundPlayer.getListenerVelocity(cameraEntity)
        if (!origin.isFinite()) return null

        // 先用 double 减去相机原点，再写 float。远离世界原点时也能保留附近声源的相对位置精度。
        attributes.`position$`().setFrom(Vec3.ZERO)
        attributes.velocity().setFrom(if (velocity.isFinite()) velocity else Vec3.ZERO)
        if (camera.isInitialized) {
            val forward = camera.lookVector
            val up = camera.upVector
            attributes.forward().set(forward.x(), forward.y(), forward.z())
            attributes.up().set(up.x(), up.y(), up.z())
        } else {
            attributes.forward().set(0f, 0f, -1f)
            attributes.up().set(0f, 1f, 0f)
        }
        return origin.takeIf { FMOD_Studio_System_SetListenerAttributes(studio, 0, attributes, null) == FMOD_OK }
    }

    /** 按 UUID 取样当前位置和世界速度。首版点声源使用固定朝向。 */
    private fun updateSource(uuid: UUID, voice: Voice, origin: Vec3, attributes: FMOD_3D_ATTRIBUTES): Boolean {
        val source = voice.soundSpreader ?: return false
        val position = source.getPosition(uuid).subtract(origin)
        if (!position.isFinite()) return false
        val velocity = if (voice.paused) Vec3.ZERO else source.getSpeed(uuid)
        attributes.`position$`().setFrom(position)
        attributes.velocity().setFrom(if (velocity.isFinite()) velocity else Vec3.ZERO)
        attributes.forward().set(0f, 0f, -1f)
        attributes.up().set(0f, 1f, 0f)
        return FMOD_Studio_EventInstance_Set3DAttributes(voice.handle, attributes) == FMOD_OK
    }

    /** 将世界向量拷贝到 FMOD 结构体。 */
    private fun FMOD_VECTOR.setFrom(vector: Vec3) {
        set(vector.x.toFloat(), vector.y.toFloat(), vector.z.toFloat())
    }

    /** 避免非有限坐标或速度进入原生混音。 */
    private fun Vec3.isFinite(): Boolean = x.isFinite() && y.isFinite() && z.isFinite()

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
        return isWritableParameter(voice, name) &&
            FMOD_Studio_EventInstance_SetParameterByName(voice.handle, name, value, 0) == FMOD_OK
    }

    /** 缓存参数的可写性，缺少可选参数时也避免重复查询 Studio。 */
    private fun isWritableParameter(voice: Voice, name: String): Boolean =
        voice.writableParameters.getOrPut(name) {
            MemoryStack.stackPush().use { stack ->
                val parameter = FMOD_STUDIO_PARAMETER_DESCRIPTION.malloc(stack)
                val blockedFlags = FMOD_STUDIO_PARAMETER_READONLY or FMOD_STUDIO_PARAMETER_AUTOMATIC or
                    FMOD_STUDIO_PARAMETER_GLOBAL
                FMOD_Studio_EventDescription_GetParameterDescriptionByName(voice.description, name, parameter) == FMOD_OK &&
                    (parameter.flags() and blockedFlags) == 0
            }
        }

    /** 绑定声源后只有播放器能驱动车内混音，未绑定声源的事件仍可自行传参。 */
    private fun usesListenerParameter(voice: Voice, name: String): Boolean =
        voice.soundSpreader != null && name.equals(INTERIOR, ignoreCase = true)

    /** 仅在听者判定改变时写参数。事件缺少可写的 interior 时继续按工程默认播放。 */
    private fun updateInterior(voice: Voice, cameraEntity: Entity?, firstPerson: Boolean) {
        val soundSpreader = voice.soundSpreader ?: return
        if (!isWritableParameter(voice, INTERIOR)) return
        val interior = soundSpreader.shouldApplyInteriorEffect(cameraEntity, firstPerson)
        if (voice.interior != interior && writeParameter(voice, INTERIOR, if (interior) 1f else 0f)) {
            voice.interior = interior
        }
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
