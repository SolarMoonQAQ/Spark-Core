package cn.solarmoon.spark_core.pack.modules

import cn.solarmoon.spark_core.client.sound.FModSoundPlayer
import cn.solarmoon.spark_core.pack.NativeLoader
import cn.solarmoon.spark_core.pack.diagnostic.PackIssue
import cn.solarmoon.spark_core.pack.diagnostic.PackLoadContext
import cn.solarmoon.spark_core.pack.diagnostic.PackSeverity
import cn.solarmoon.spark_core.pack.diagnostic.SparkPackIssues
import cn.solarmoon.spark_core.pack.graph.SparkPackage
import net.minecraft.network.chat.Component
import net.minecraft.resources.ResourceLocation
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.neoforge.event.GameShuttingDownEvent
import org.lwjgl.fmod.FMOD.*
import org.lwjgl.fmod.FMODStudio.*
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.util.Locale

/**
 * 客户端 FMOD bank 模块。
 *
 * 读取 `<命名空间>/fmod/` 及其子目录中的 `.bank` 文件。bank ID 使用命名空间加模块内路径，
 * 去掉后缀并转为小写。例如 `spark_core/fmod/engines/Drive.bank` 对应 `spark_core:engines/drive`。
 * 包之间沿用内容包依赖顺序，包内按 bank ID 排序。所有装载完成后统一公布可用的 bank。
 */
object FModModule : SparkPackModule {
    override val id = "fmod"
    override val mode = ReadMode.CLIENT_LOCAL_ONLY

    private val pending = linkedMapOf<ResourceLocation, MutableMap<ResourceLocation, ByteArray>>()
    /** 原生句柄仅可在持有本模块锁时使用，避免与重载或退出时的释放交错。 */
    internal var studio = 0L
        private set
    @Volatile
    internal var loadedBanks: Map<ResourceLocation, Long> = emptyMap()
        private set

    /** 查询当前客户端会话中是否已加载指定 bank。 */
    @JvmStatic
    fun isBankLoaded(bank: ResourceLocation): Boolean = bank in loadedBanks

    /** 开始收集本轮客户端的 bank。已加载的 bank 在应用阶段统一释放。 */
    override fun onStart(isClientSide: Boolean, fromServer: Boolean) {
        if (isClientSide && !fromServer) pending.clear()
    }

    /** 从 bank 的文件路径生成 ID，并按所属内容包归组。 */
    override fun read(
        namespace: String,
        pathSegments: List<String>,
        fileName: String,
        content: ByteArray,
        pack: SparkPackage,
        isClientSide: Boolean,
        fromServer: Boolean
    ) {
        if (!isClientSide || fromServer || !fileName.endsWith(".bank", ignoreCase = true)) return
        val relativePath = (pathSegments + fileName).joinToString("/")
        val bankId = ResourceLocation.fromNamespaceAndPath(
            namespace, relativePath.lowercase(Locale.ROOT).removeSuffix(".bank")
        )
        val banks = pending.getOrPut(pack.meta.id) { linkedMapOf() }
        // 归一化后仍以 ID 唯一标识 bank，避免大小写不同的文件静默覆盖。
        require(banks.putIfAbsent(bankId, content) == null) { "内容包内的 FMOD bank ID 重复：$bankId" }
    }

    /** 应用本轮收集的 bank。FMOD 不可用时保留空索引，让其余内容包模块继续工作。 */
    @Synchronized
    override fun onFinish(isClientSide: Boolean, fromServer: Boolean) {
        if (!isClientSide || fromServer) return
        try {
            releaseStudio()
            if (pending.isEmpty() || !NativeLoader.loadFmod()) return

            MemoryStack.stackPush().use { stack ->
                val handle = stack.mallocPointer(1)
                checkFmod("创建 Studio System", FMOD_Studio_System_Create(handle, FMOD_VERSION))
                studio = handle[0]
                // Minecraft 世界坐标和相机向量沿用右手系。
                checkFmod("初始化 Studio System",
                    FMOD_Studio_System_Initialize(studio, 64, FMOD_STUDIO_INIT_NORMAL, FMOD_INIT_3D_RIGHTHANDED, 0L))

                // 包的首次出现顺序由内容包加载器决定。一个包加载失败时，保留其它包的 bank。
                val loaded = linkedMapOf<ResourceLocation, Long>()
                for ((packId, banks) in pending) {
                    val duplicate = banks.keys.firstOrNull { it in loaded }
                    if (duplicate != null) {
                        warn(packId, duplicate, "FMOD bank ID 已由其它内容包加载：$duplicate")
                        continue
                    }
                    loaded += loadPackBanks(packId, banks)
                }
                if (loaded.isEmpty()) {
                    releaseStudio()
                } else {
                    checkFmod("更新 Studio System", FMOD_Studio_System_Update(studio))
                    loadedBanks = loaded.toMap()
                }
            }
        } catch (e: Exception) {
            releaseStudio()
            SparkPackIssues.warn(Component.literal("FMOD bank 加载不可用：${e.message}"), e)
        } catch (e: LinkageError) {
            releaseStudio()
            SparkPackIssues.warn(Component.literal("FMOD 绑定不可用：${e.message}"), e)
        } finally {
            pending.clear()
        }
    }

    /** 关闭客户端时释放 Studio System 及其持有的全部 bank。 */
    @SubscribeEvent
    @Synchronized
    fun onGameShuttingDown(event: GameShuttingDownEvent) {
        releaseStudio()
    }

    /** 更新失败时释放当前会话，使后续播放请求能走回退。播放器在持锁时调用。 */
    internal fun disablePlayback(result: Int) {
        SparkPackIssues.warn(Component.literal("FMOD 播放不可用：" + resultMessage(result)))
        releaseStudio()
    }

    /** 加载一个包的 bank。任一 bank 加载失败时撤销该包的本轮装载。 */
    private fun loadPackBanks(
        packId: ResourceLocation,
        banks: Map<ResourceLocation, ByteArray>
    ): Map<ResourceLocation, Long> = MemoryStack.stackPush().use { stack ->
        val handle = stack.mallocPointer(1)
        val loaded = linkedMapOf<ResourceLocation, Long>()
        // 按 ID 排序，使包内的次序独立于目录遍历或 ZIP 条目顺序。
        for ((bank, bytes) in banks.entries.sortedBy { it.key.toString() }) {
            val buffer = MemoryUtil.memAlloc(bytes.size)
            val result = try {
                buffer.put(bytes).flip()
                // COPY 模式由 FMOD 拷贝输入，调用返回后即可释放临时直接缓冲区。
                FMOD_Studio_System_LoadBankMemory(
                    studio, buffer, FMOD_STUDIO_LOAD_MEMORY, FMOD_STUDIO_LOAD_BANK_NORMAL, handle
                )
            } finally {
                MemoryUtil.memFree(buffer)
            }
            if (result != FMOD_OK) {
                warn(packId, bank, "无法加载 FMOD bank $bank：${resultMessage(result)}")
                loaded.values.toList().asReversed().forEach {
                    checkFmod("撤销 FMOD bank", FMOD_Studio_Bank_Unload(it))
                }
                if (loaded.isNotEmpty()) {
                    checkFmod("提交 FMOD bank 卸载", FMOD_Studio_System_FlushCommands(studio))
                }
                return@use emptyMap()
            }
            loaded[bank] = handle[0]
        }
        loaded
    }

    /** 清空查询索引，再释放本轮持有的 Studio System。 */
    private fun releaseStudio() {
        loadedBanks = emptyMap()
        val handle = studio
        studio = 0L
        if (handle == 0L) return
        // 原生 System 会释放全部实例，客户端同时丢弃旧 UUID 和声源回调。
        FModSoundPlayer.onStudioReleased()
        try {
            checkFmod("释放 Studio System", FMOD_Studio_System_Release(handle))
        } catch (e: Exception) {
            SparkPackIssues.warn(Component.literal("无法释放 FMOD Studio System：${e.message}"), e)
        } catch (e: LinkageError) {
            SparkPackIssues.warn(Component.literal("无法链接 FMOD Studio 释放入口：${e.message}"), e)
        }
    }

    /** 记录带内容包 ID 和 bank ID 的加载警告。 */
    private fun warn(packId: ResourceLocation, bank: ResourceLocation, message: String) {
        SparkPackIssues.report(PackIssue(id, packId.toString(), bank.toString(), PackSeverity.WARN,
            Component.literal(message), null, PackLoadContext.CLIENT_LOCAL))
    }

    /** 将 Studio 生命周期操作的非成功结果交给模块的故障回退。 */
    private fun checkFmod(operation: String, result: Int) {
        check(result == FMOD_OK) { "$operation：${resultMessage(result)}" }
    }

    /** 为 bank 版本不兼容提供可执行的诊断，其它错误保留 FMOD 返回码。 */
    private fun resultMessage(result: Int): String = when (result) {
        FMOD_ERR_VERSION -> "FMOD_ERR_VERSION，当前运行库需要 FMOD Studio 2.03 兼容的 bank"
        else -> "FMOD 返回码 $result"
    }
}
