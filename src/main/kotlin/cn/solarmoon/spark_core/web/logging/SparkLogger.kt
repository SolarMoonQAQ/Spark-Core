package cn.solarmoon.spark_core.web.logging

import org.slf4j.Logger
import org.slf4j.Marker

/**
 * SparkCore增强日志记录器
 * 包装原始Logger，同时将日志发送到LogCollector进行收集
 */
class SparkLogger(private val originalLogger: Logger) : Logger {
    
    override fun getName(): String = originalLogger.name
    
    override fun isTraceEnabled(): Boolean = originalLogger.isTraceEnabled
    override fun isTraceEnabled(marker: Marker?): Boolean = originalLogger.isTraceEnabled(marker)
    
    override fun trace(msg: String?) {
        originalLogger.trace(msg)
        msg?.let { LogCollector.addLogEntry("TRACE", name, it) }
    }
    
    override fun trace(format: String?, arg: Any?) {
        originalLogger.trace(format, arg)
        format?.let { LogCollector.addLogEntry("TRACE", name, formatMessage(it, arg)) }
    }
    
    override fun trace(format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.trace(format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("TRACE", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun trace(format: String?, vararg arguments: Any?) {
        originalLogger.trace(format, *arguments)
        format?.let { LogCollector.addLogEntry("TRACE", name, formatMessage(it, *arguments)) }
    }
    
    override fun trace(msg: String?, t: Throwable?) {
        originalLogger.trace(msg, t)
        msg?.let { LogCollector.addLogEntry("TRACE", name, it, t) }
    }
    
    override fun trace(marker: Marker?, msg: String?) {
        originalLogger.trace(marker, msg)
        msg?.let { LogCollector.addLogEntry("TRACE", name, it) }
    }
    
    override fun trace(marker: Marker?, format: String?, arg: Any?) {
        originalLogger.trace(marker, format, arg)
        format?.let { LogCollector.addLogEntry("TRACE", name, formatMessage(it, arg)) }
    }
    
    override fun trace(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.trace(marker, format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("TRACE", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun trace(marker: Marker?, format: String?, vararg argArray: Any?) {
        originalLogger.trace(marker, format, *argArray)
        format?.let { LogCollector.addLogEntry("TRACE", name, formatMessage(it, *argArray)) }
    }
    
    override fun trace(marker: Marker?, msg: String?, t: Throwable?) {
        originalLogger.trace(marker, msg, t)
        msg?.let { LogCollector.addLogEntry("TRACE", name, it, t) }
    }
    
    override fun isDebugEnabled(): Boolean = originalLogger.isDebugEnabled
    override fun isDebugEnabled(marker: Marker?): Boolean = originalLogger.isDebugEnabled(marker)
    
    override fun debug(msg: String?) {
        originalLogger.debug(msg)
        msg?.let { LogCollector.addLogEntry("DEBUG", name, it) }
    }
    
    override fun debug(format: String?, arg: Any?) {
        originalLogger.debug(format, arg)
        format?.let { LogCollector.addLogEntry("DEBUG", name, formatMessage(it, arg)) }
    }
    
    override fun debug(format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.debug(format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("DEBUG", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun debug(format: String?, vararg arguments: Any?) {
        originalLogger.debug(format, *arguments)
        format?.let { LogCollector.addLogEntry("DEBUG", name, formatMessage(it, *arguments)) }
    }
    
    override fun debug(msg: String?, t: Throwable?) {
        originalLogger.debug(msg, t)
        msg?.let { LogCollector.addLogEntry("DEBUG", name, it, t) }
    }
    
    override fun debug(marker: Marker?, msg: String?) {
        originalLogger.debug(marker, msg)
        msg?.let { LogCollector.addLogEntry("DEBUG", name, it) }
    }
    
    override fun debug(marker: Marker?, format: String?, arg: Any?) {
        originalLogger.debug(marker, format, arg)
        format?.let { LogCollector.addLogEntry("DEBUG", name, formatMessage(it, arg)) }
    }
    
    override fun debug(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.debug(marker, format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("DEBUG", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun debug(marker: Marker?, format: String?, vararg argArray: Any?) {
        originalLogger.debug(marker, format, *argArray)
        format?.let { LogCollector.addLogEntry("DEBUG", name, formatMessage(it, *argArray)) }
    }
    
    override fun debug(marker: Marker?, msg: String?, t: Throwable?) {
        originalLogger.debug(marker, msg, t)
        msg?.let { LogCollector.addLogEntry("DEBUG", name, it, t) }
    }
    
    override fun isInfoEnabled(): Boolean = originalLogger.isInfoEnabled
    override fun isInfoEnabled(marker: Marker?): Boolean = originalLogger.isInfoEnabled(marker)
    
    override fun info(msg: String?) {
        originalLogger.info(msg)
        msg?.let { LogCollector.addLogEntry("INFO", name, it) }
    }
    
    override fun info(format: String?, arg: Any?) {
        originalLogger.info(format, arg)
        format?.let { LogCollector.addLogEntry("INFO", name, formatMessage(it, arg)) }
    }
    
    override fun info(format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.info(format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("INFO", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun info(format: String?, vararg arguments: Any?) {
        originalLogger.info(format, *arguments)
        format?.let { LogCollector.addLogEntry("INFO", name, formatMessage(it, *arguments)) }
    }
    
    override fun info(msg: String?, t: Throwable?) {
        originalLogger.info(msg, t)
        msg?.let { LogCollector.addLogEntry("INFO", name, it, t) }
    }
    
    override fun info(marker: Marker?, msg: String?) {
        originalLogger.info(marker, msg)
        msg?.let { LogCollector.addLogEntry("INFO", name, it) }
    }
    
    override fun info(marker: Marker?, format: String?, arg: Any?) {
        originalLogger.info(marker, format, arg)
        format?.let { LogCollector.addLogEntry("INFO", name, formatMessage(it, arg)) }
    }
    
    override fun info(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.info(marker, format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("INFO", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun info(marker: Marker?, format: String?, vararg argArray: Any?) {
        originalLogger.info(marker, format, *argArray)
        format?.let { LogCollector.addLogEntry("INFO", name, formatMessage(it, *argArray)) }
    }
    
    override fun info(marker: Marker?, msg: String?, t: Throwable?) {
        originalLogger.info(marker, msg, t)
        msg?.let { LogCollector.addLogEntry("INFO", name, it, t) }
    }
    
    override fun isWarnEnabled(): Boolean = originalLogger.isWarnEnabled
    override fun isWarnEnabled(marker: Marker?): Boolean = originalLogger.isWarnEnabled(marker)
    
    override fun warn(msg: String?) {
        originalLogger.warn(msg)
        msg?.let { LogCollector.addLogEntry("WARN", name, it) }
    }
    
    override fun warn(format: String?, arg: Any?) {
        originalLogger.warn(format, arg)
        format?.let { LogCollector.addLogEntry("WARN", name, formatMessage(it, arg)) }
    }
    
    override fun warn(format: String?, vararg arguments: Any?) {
        originalLogger.warn(format, *arguments)
        format?.let { LogCollector.addLogEntry("WARN", name, formatMessage(it, *arguments)) }
    }
    
    override fun warn(format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.warn(format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("WARN", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun warn(msg: String?, t: Throwable?) {
        originalLogger.warn(msg, t)
        msg?.let { LogCollector.addLogEntry("WARN", name, it, t) }
    }
    
    override fun warn(marker: Marker?, msg: String?) {
        originalLogger.warn(marker, msg)
        msg?.let { LogCollector.addLogEntry("WARN", name, it) }
    }
    
    override fun warn(marker: Marker?, format: String?, arg: Any?) {
        originalLogger.warn(marker, format, arg)
        format?.let { LogCollector.addLogEntry("WARN", name, formatMessage(it, arg)) }
    }
    
    override fun warn(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.warn(marker, format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("WARN", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun warn(marker: Marker?, format: String?, vararg argArray: Any?) {
        originalLogger.warn(marker, format, *argArray)
        format?.let { LogCollector.addLogEntry("WARN", name, formatMessage(it, *argArray)) }
    }
    
    override fun warn(marker: Marker?, msg: String?, t: Throwable?) {
        originalLogger.warn(marker, msg, t)
        msg?.let { LogCollector.addLogEntry("WARN", name, it, t) }
    }
    
    override fun isErrorEnabled(): Boolean = originalLogger.isErrorEnabled
    override fun isErrorEnabled(marker: Marker?): Boolean = originalLogger.isErrorEnabled(marker)
    
    override fun error(msg: String?) {
        originalLogger.error(msg)
        msg?.let { LogCollector.addLogEntry("ERROR", name, it) }
    }
    
    override fun error(format: String?, arg: Any?) {
        originalLogger.error(format, arg)
        format?.let { LogCollector.addLogEntry("ERROR", name, formatMessage(it, arg)) }
    }
    
    override fun error(format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.error(format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("ERROR", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun error(format: String?, vararg arguments: Any?) {
        originalLogger.error(format, *arguments)
        format?.let { LogCollector.addLogEntry("ERROR", name, formatMessage(it, *arguments)) }
    }
    
    override fun error(msg: String?, t: Throwable?) {
        originalLogger.error(msg, t)
        msg?.let { LogCollector.addLogEntry("ERROR", name, it, t) }
    }
    
    override fun error(marker: Marker?, msg: String?) {
        originalLogger.error(marker, msg)
        msg?.let { LogCollector.addLogEntry("ERROR", name, it) }
    }
    
    override fun error(marker: Marker?, format: String?, arg: Any?) {
        originalLogger.error(marker, format, arg)
        format?.let { LogCollector.addLogEntry("ERROR", name, formatMessage(it, arg)) }
    }
    
    override fun error(marker: Marker?, format: String?, arg1: Any?, arg2: Any?) {
        originalLogger.error(marker, format, arg1, arg2)
        format?.let { LogCollector.addLogEntry("ERROR", name, formatMessage(it, arg1, arg2)) }
    }
    
    override fun error(marker: Marker?, format: String?, vararg argArray: Any?) {
        originalLogger.error(marker, format, *argArray)
        format?.let { LogCollector.addLogEntry("ERROR", name, formatMessage(it, *argArray)) }
    }
    
    override fun error(marker: Marker?, msg: String?, t: Throwable?) {
        originalLogger.error(marker, msg, t)
        msg?.let { LogCollector.addLogEntry("ERROR", name, it, t) }
    }
    
    /**
     * 格式化消息，处理SLF4J的占位符
     */
    private fun formatMessage(format: String, vararg args: Any?): String {
        return try {
            var result = format
            args.forEach { arg ->
                result = result.replaceFirst("{}", arg?.toString() ?: "null")
            }
            result
        } catch (e: Exception) {
            "$format [格式化失败: ${e.message}]"
        }
    }

    // ==================== 日志搜索接口 ====================

    /**
     * 搜索日志条目
     * 直接委托给LogCollector，提供统一的搜索入口
     */
    fun searchLogs(request: LogSearchRequest): List<LogEntry> {
        return LogCollector.searchLogs(request)
    }

    /**
     * 获取日志统计信息
     */
    fun getLogStats(): Map<String, Any?> {
        return LogCollector.getLogStats()
    }

    /**
     * 清空日志
     */
    fun clearLogs() {
        LogCollector.clearLogs()
    }

    /**
     * 手动添加日志条目（用于特殊情况）
     */
    fun addLogEntry(level: String, logger: String, message: String, exception: Throwable? = null) {
        LogCollector.addLogEntry(level, logger, message, exception)
    }
}
