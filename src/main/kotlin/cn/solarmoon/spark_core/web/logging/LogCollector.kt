package cn.solarmoon.spark_core.web.logging

import cn.solarmoon.spark_core.SparkCore
import net.neoforged.fml.loading.FMLEnvironment
import org.slf4j.event.Level
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicLong
import java.util.regex.Pattern

/**
 * 日志条目数据类
 */
data class LogEntry(
    val id: Long,
    val timestamp: String,
    val level: String,
    val side: String, // "CLIENT" 或 "SERVER"
    val logger: String,
    val message: String,
    val thread: String,
    val exception: String? = null
)

/**
 * 日志搜索请求
 */
data class LogSearchRequest(
    val level: String? = null, // DEBUG, INFO, WARN, ERROR
    val side: String? = null, // CLIENT, SERVER
    val regex: String? = null, // 正则表达式搜索
    val maxLines: Int = 1000, // 最大行数限制
    val maxChars: Int = 100000 // 最大字符数限制
)

/**
 * 日志收集器
 * 收集当前游戏会话的日志信息，支持搜索和过滤
 */
object LogCollector {
    
    private val logEntries = ConcurrentLinkedQueue<LogEntry>()
    private val idGenerator = AtomicLong(0)
    private val maxLogEntries = 10000 // 最大保存的日志条目数
    
    // 当前运行环境
    private val currentSide = if (FMLEnvironment.dist.isClient()) "CLIENT" else "SERVER"
    
    /**
     * 添加日志条目
     */
    fun addLogEntry(level: String, logger: String, message: String, exception: Throwable? = null) {
        val entry = LogEntry(
            id = idGenerator.incrementAndGet(),
            timestamp = Instant.now().toString(),
            level = level,
            side = currentSide,
            logger = logger,
            message = message,
            thread = Thread.currentThread().name,
            exception = exception?.let { formatException(it) }
        )
        
        logEntries.offer(entry)
        
        // 限制日志条目数量，移除最旧的条目
        while (logEntries.size > maxLogEntries) {
            logEntries.poll()
        }
    }
    
    /**
     * 搜索日志
     */
    fun searchLogs(request: LogSearchRequest): List<LogEntry> {
        var results = logEntries.toList()
        
        // 按级别过滤
        request.level?.let { level ->
            results = results.filter { it.level.equals(level, ignoreCase = true) }
        }
        
        // 按运行环境过滤
        request.side?.let { side ->
            results = results.filter { it.side.equals(side, ignoreCase = true) }
        }
        
        // 正则表达式搜索
        request.regex?.let { regex ->
            try {
                val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
                results = results.filter { entry ->
                    pattern.matcher(entry.message).find() ||
                    pattern.matcher(entry.logger).find() ||
                    (entry.exception != null && pattern.matcher(entry.exception).find())
                }
            } catch (e: Exception) {
                SparkCore.LOGGER.warn("无效的正则表达式: $regex", e)
                // 如果正则表达式无效，回退到简单的字符串包含搜索
                results = results.filter { entry ->
                    entry.message.contains(regex, ignoreCase = true) ||
                    entry.logger.contains(regex, ignoreCase = true) ||
                    (entry.exception != null && entry.exception.contains(regex, ignoreCase = true))
                }
            }
        }
        
        // 按时间倒序排列（最新的在前）
        results = results.sortedByDescending { it.id }
        
        // 应用行数限制
        if (results.size > request.maxLines) {
            results = results.take(request.maxLines)
        }
        
        // 应用字符数限制
        val maxChars = request.maxChars
        var totalChars = 0
        val limitedResults = mutableListOf<LogEntry>()
        
        for (entry in results) {
            val entrySize = entry.message.length + (entry.exception?.length ?: 0) + 100 // 额外的元数据字符
            if (totalChars + entrySize > maxChars) {
                break
            }
            limitedResults.add(entry)
            totalChars += entrySize
        }
        
        return limitedResults
    }
    
    /**
     * 获取日志统计信息
     */
    fun getLogStats(): Map<String, Any?> {
        val entries = logEntries.toList()
        val levelCounts = entries.groupingBy { it.level }.eachCount()
        val sideCounts = entries.groupingBy { it.side }.eachCount()

        return mapOf(
            "total_entries" to entries.size,
            "max_entries" to maxLogEntries,
            "current_side" to currentSide,
            "level_counts" to levelCounts,
            "side_counts" to sideCounts,
            "oldest_entry" to entries.minByOrNull { it.id }?.timestamp,
            "newest_entry" to entries.maxByOrNull { it.id }?.timestamp
        )
    }
    
    /**
     * 清空日志
     */
    fun clearLogs() {
        logEntries.clear()
        SparkCore.LOGGER.info("日志收集器已清空")
    }
    
    /**
     * 格式化异常信息
     */
    private fun formatException(exception: Throwable): String {
        val sw = java.io.StringWriter()
        val pw = java.io.PrintWriter(sw)
        exception.printStackTrace(pw)
        return sw.toString()
    }
}
