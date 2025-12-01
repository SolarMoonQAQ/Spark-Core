package cn.solarmoon.spark_core.util.sound.filter

import cn.solarmoon.spark_core.sound.SoundData
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * 延迟滤波器
 *
 * 功能：对音频信号应用固定延迟效果
 * 用途：创造回声效果、模拟距离感、添加空间感
 *
 * 说明：本实现针对离线音频处理优化，使用数组存储历史数据而非环形缓冲区
 * 以确保精度和可重复性，资源释放由GC自动管理
 */
class DelayFilter {

    companion object {

        /**
         * 应用延迟效果到声音数据
         * @param soundData 原始声音数据
         * @param delay 延迟时间(秒)
         * @param dryMix 干信号混合比例(0.0-1.0)
         * @param wetMix 湿信号混合比例(0.0-1.0)
         * @return 应用延迟效果后的声音数据
         */
        @JvmStatic
        @JvmOverloads
        fun applyDelay(
            soundData: SoundData,
            delay: Double = 0.5,
            dryMix: Double = 0.7,
            wetMix: Double = 0.3
        ): SoundData {
            val buffer = soundData.byteBuffer()
            val format = soundData.audioFormat()
            val sampleRate = format.sampleRate.toInt()

            // 计算延迟样本数并确保为整数
            val delaySamples = (delay * sampleRate).toInt().coerceAtLeast(0)

            // 如果延迟为0，直接返回原始数据
            if (delaySamples == 0) {
                return soundData
            }

            // 创建新的缓冲区用于存储处理后的数据
            val totalSamples = buffer.capacity() / 2
            val outputBuffer = ByteBuffer.allocateDirect(buffer.capacity())
                .order(ByteOrder.LITTLE_ENDIAN)

            // 使用Double数组进行计算以保证精度
            val outputSamples = DoubleArray(totalSamples) { 0.0 }

            // 处理每个样本
            for (i in 0 until totalSamples) {
                // 获取当前输入样本（干信号）
                val drySample = buffer.getShort(i * 2).toDouble() / Short.MAX_VALUE.toDouble()

                // 获取延迟样本（湿信号），如果没有延迟样本则为0
                val wetSample = if (i >= delaySamples) {
                    buffer.getShort((i - delaySamples) * 2).toDouble() / Short.MAX_VALUE.toDouble()
                } else {
                    0.0
                }

                // 混合干湿信号
                val mixedSample = (drySample * dryMix) + (wetSample * wetMix)

                // 将混合结果裁剪到安全范围
                val clampedSample = mixedSample.coerceIn(-1.0, 1.0)
                outputSamples[i] = clampedSample
            }

            // 将Double样本写回字节缓冲区
            for (i in 0 until totalSamples) {
                val sampleValue = (outputSamples[i] * Short.MAX_VALUE).toInt()
                outputBuffer.putShort(i * 2, sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }

            outputBuffer.rewind()
            return SoundData(outputBuffer, format)
        }

        /**
         * 应用多抽头延迟效果（带多个回声）
         * @param soundData 原始声音数据
         * @param delays 多个延迟时间数组(秒)
         * @param gains 每个延迟信号的增益数组
         * @param dryGain 干信号增益
         * @return 应用多延迟效果后的声音数据
         */
        @JvmStatic
        @JvmOverloads
        fun applyMultiTapDelay(
            soundData: SoundData,
            delays: DoubleArray = doubleArrayOf(0.3, 0.6, 0.9),
            gains: DoubleArray = doubleArrayOf(0.5, 0.3, 0.1),
            dryGain: Double = 0.7
        ): SoundData {
            require(delays.size == gains.size) { "延迟时间和增益数组长度必须相同" }

            val buffer = soundData.byteBuffer()
            val format = soundData.audioFormat()
            val sampleRate = format.sampleRate.toInt()
            val totalSamples = buffer.capacity() / 2

            // 计算每个延迟对应的样本数
            val delaySamples = IntArray(delays.size)
            for (i in delays.indices) {
                delaySamples[i] = (delays[i] * sampleRate).toInt().coerceAtLeast(0)
            }

            val outputBuffer = ByteBuffer.allocateDirect(buffer.capacity())
                .order(ByteOrder.LITTLE_ENDIAN)

            // 使用Double数组进行计算以保证精度
            val outputSamples = DoubleArray(totalSamples) { 0.0 }

            // 处理每个样本
            for (i in 0 until totalSamples) {
                // 获取当前输入样本（干信号）
                val drySample = buffer.getShort(i * 2).toDouble() / Short.MAX_VALUE.toDouble()

                // 初始化混合样本为干信号
                var mixedSample = drySample * dryGain

                // 添加每个延迟抽头的信号
                for (tap in delays.indices) {
                    val delay = delaySamples[tap]
                    val gain = gains[tap]

                    if (i >= delay) {
                        val delayedSample = buffer.getShort((i - delay) * 2).toDouble() / Short.MAX_VALUE.toDouble()
                        mixedSample += delayedSample * gain
                    }
                }

                // 将混合结果裁剪到安全范围
                outputSamples[i] = mixedSample.coerceIn(-1.0, 1.0)
            }

            // 应用峰值归一化防止削波
            val maxAmplitude = outputSamples.maxByOrNull { kotlin.math.abs(it) } ?: 0.0
            val peak = kotlin.math.abs(maxAmplitude)
            val scaleFactor = if (peak > 1.0) 1.0 / peak else 1.0

            // 将Double样本写回字节缓冲区
            for (i in 0 until totalSamples) {
                val scaledSample = outputSamples[i] * scaleFactor
                val sampleValue = (scaledSample * Short.MAX_VALUE).toInt()
                outputBuffer.putShort(i * 2, sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }

            outputBuffer.rewind()
            return SoundData(outputBuffer, format)
        }

        /**
         * 应用反馈延迟（回声效果）
         * @param soundData 原始声音数据
         * @param delayTime 延迟时间(秒)
         * @param feedback 反馈系数(0.0-1.0)
         * @param iterations 迭代次数（回声数量）
         * @return 应用反馈延迟后的声音数据
         */
        @JvmStatic
        @JvmOverloads
        fun applyFeedbackDelay(
            soundData: SoundData,
            delayTime: Double = 0.3,
            feedback: Double = 0.5,
            iterations: Int = 5
        ): SoundData {
            val buffer = soundData.byteBuffer()
            val format = soundData.audioFormat()
            val sampleRate = format.sampleRate.toInt()
            val totalSamples = buffer.capacity() / 2

            // 计算延迟样本数
            val delaySamples = (delayTime * sampleRate).toInt().coerceAtLeast(0)

            // 扩展输出缓冲区以容纳所有回声
            val extendedSamples = totalSamples + (delaySamples * iterations)
            val extendedBuffer = ByteBuffer.allocateDirect(extendedSamples * 2)
                .order(ByteOrder.LITTLE_ENDIAN)

            // 使用Double数组进行计算以保证精度
            val outputSamples = DoubleArray(extendedSamples) { 0.0 }

            // 复制原始信号
            for (i in 0 until totalSamples) {
                val sample = buffer.getShort(i * 2).toDouble() / Short.MAX_VALUE.toDouble()
                outputSamples[i] = sample
            }

            // 生成回声
            for (echo in 1..iterations) {
                val echoStart = delaySamples * echo
                val echoGain = Math.pow(feedback, echo.toDouble())

                for (i in 0 until totalSamples) {
                    val targetIndex = echoStart + i
                    if (targetIndex < extendedSamples) {
                        val originalSample = buffer.getShort(i * 2).toDouble() / Short.MAX_VALUE.toDouble()
                        outputSamples[targetIndex] += originalSample * echoGain
                    }
                }
            }

            // 裁剪到安全范围
            for (i in 0 until extendedSamples) {
                outputSamples[i] = outputSamples[i].coerceIn(-1.0, 1.0)
            }

            // 应用峰值归一化
            val maxAmplitude = outputSamples.maxByOrNull { kotlin.math.abs(it) } ?: 0.0
            val peak = kotlin.math.abs(maxAmplitude)
            val scaleFactor = if (peak > 1.0) 1.0 / peak else 1.0

            // 将Double样本写回字节缓冲区
            for (i in 0 until extendedSamples) {
                val scaledSample = outputSamples[i] * scaleFactor
                val sampleValue = (scaledSample * Short.MAX_VALUE).toInt()
                extendedBuffer.putShort(i * 2, sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
            }

            extendedBuffer.rewind()
            // 创建新的音频格式，长度已改变
            return SoundData(extendedBuffer, format)
        }
    }
}