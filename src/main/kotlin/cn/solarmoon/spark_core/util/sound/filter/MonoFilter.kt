package cn.solarmoon.spark_core.util.sound.filter

import cn.solarmoon.spark_core.sound.SoundData
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.AudioFormat

/**
 * 单声道音频滤波器
 * 专门在频域处理单声道音频波形数据
 */
object MonoFilter {

    /**
     * 应用低通滤波器到声音数据
     * 用途：模拟闷响、远距离声音，去除高频刺耳声
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @param cutoff 截止频率(Hz)，默认1000.0
     * @param resonance 共振强度(0.0-1.0)，默认0.5
     * @param sampleRate 采样率(Hz)，默认44100
     * @return 滤波后的样本数组
     */
    @JvmStatic
    @JvmOverloads
    fun lowPassFilter(
        samples: DoubleArray,
        cutoff: Double = 1000.0,
        resonance: Double = 0.5,
        sampleRate: Double = 44100.0
    ): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        val dt = 1.0 / sampleRate
        val RC = 1.0 / (2.0 * Math.PI * cutoff)
        val alpha = dt / (RC + dt)

        val result = DoubleArray(samples.size)
        var prevOutput = 0.0

        for (i in samples.indices) {
            val output = prevOutput + alpha * (samples[i] - prevOutput)
            prevOutput = output

            val resonated = output * (1.0 + resonance * 0.1).coerceAtMost(1.5)
            result[i] = resonated.coerceIn(-1.0, 1.0)
        }

        return result
    }

    /**
     * 应用高通滤波器到声音数据
     * 用途：去除低频嗡嗡声，创造薄脆音色
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @param cutoff 截止频率(Hz)，默认200.0
     * @param sampleRate 采样率(Hz)，默认44100
     * @return 滤波后的样本数组
     */
    @JvmStatic
    @JvmOverloads
    fun highPassFilter(
        samples: DoubleArray,
        cutoff: Double = 200.0,
        sampleRate: Double = 44100.0
    ): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        val rc = 1.0 / (2.0 * Math.PI * cutoff)
        val dt = 1.0 / sampleRate
        val alpha = rc / (rc + dt)

        val result = DoubleArray(samples.size)
        var prevInput = 0.0
        var prevOutput = 0.0

        for (i in samples.indices) {
            val output = alpha * (prevOutput + samples[i] - prevInput)
            result[i] = output.coerceIn(-1.0, 1.0)

            prevInput = samples[i]
            prevOutput = output
        }

        return result
    }

    /**
     * 应用带通滤波器到声音数据
     * 用途：突出特定频段，模拟电话音、特定共振
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @param centerFreq 中心频率(Hz)，默认1000.0
     * @param bandwidth 带宽(Hz)，默认500.0
     * @param sampleRate 采样率(Hz)，默认44100
     * @return 滤波后的样本数组
     */
    @JvmStatic
    @JvmOverloads
    fun bandPassFilter(
        samples: DoubleArray,
        centerFreq: Double = 1000.0,
        bandwidth: Double = 500.0,
        sampleRate: Double = 44100.0
    ): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        val R = 1.0 - 3.0 * bandwidth / sampleRate
        val cosTheta = (2.0 * R * Math.cos(2.0 * Math.PI * centerFreq / sampleRate)) / (1.0 + R * R)
        val alpha = (1.0 - R * R) * Math.sin(2.0 * Math.PI * centerFreq / sampleRate) / 2.0

        val result = DoubleArray(samples.size)
        var prevInput1 = 0.0
        var prevInput2 = 0.0
        var prevOutput1 = 0.0
        var prevOutput2 = 0.0

        for (i in samples.indices) {
            val output = alpha * (samples[i] - prevInput2) +
                    2.0 * R * cosTheta * prevOutput1 -
                    R * R * prevOutput2

            result[i] = output.coerceIn(-1.0, 1.0)

            prevInput2 = prevInput1
            prevInput1 = samples[i]
            prevOutput2 = prevOutput1
            prevOutput1 = output
        }

        return result
    }

    /**
     * 应用直流滤波器去除音频数据中的直流偏移
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @return 去除直流偏移后的样本数组
     */
    @JvmStatic
    fun dcFilter(samples: DoubleArray): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        // 计算直流偏移（平均值）
        val dcOffset = samples.average()

        // 减去直流偏移
        return DoubleArray(samples.size) { i ->
            (samples[i] - dcOffset).coerceIn(-1.0, 1.0)
        }
    }

    /**
     * 应用延迟效果到样本数组
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @param delay 延迟时间(秒)
     * @param dryMix 干信号混合比例(0.0-1.0)
     * @param wetMix 湿信号混合比例(0.0-1.0)
     * @param sampleRate 采样率(Hz)，默认44100
     * @return 应用延迟效果后的样本数组
     */
    @JvmStatic
    @JvmOverloads
    fun applyDelay(
        samples: DoubleArray,
        delay: Double = 0.5,
        dryMix: Double = 0.7,
        wetMix: Double = 0.3,
        sampleRate: Int = 44100
    ): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        // 计算延迟样本数并确保为整数
        val delaySamples = (delay * sampleRate).toInt().coerceAtLeast(0)

        // 如果延迟为0，直接返回原始数据
        if (delaySamples == 0) {
            return samples.copyOf()
        }

        val totalSamples = samples.size
        val result = DoubleArray(totalSamples)

        // 处理每个样本
        for (i in 0 until totalSamples) {
            // 获取当前输入样本（干信号）
            val drySample = samples[i]

            // 获取延迟样本（湿信号），如果没有延迟样本则为0
            val wetSample = if (i >= delaySamples) {
                samples[i - delaySamples]
            } else {
                0.0
            }

            // 混合干湿信号
            val mixedSample = (drySample * dryMix) + (wetSample * wetMix)

            // 将混合结果裁剪到安全范围
            result[i] = mixedSample.coerceIn(-1.0, 1.0)
        }

        return result
    }

    /**
     * 应用多抽头延迟效果（带多个回声）
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @param delays 多个延迟时间数组(秒)
     * @param gains 每个延迟信号的增益数组
     * @param dryGain 干信号增益，默认0.7
     * @param sampleRate 采样率(Hz)，默认44100
     * @param autoNormalize 是否自动归一化，默认true
     * @return 应用多延迟效果后的样本数组
     */
    @JvmStatic
    @JvmOverloads
    fun applyMultiTapDelay(
        samples: DoubleArray,
        delays: DoubleArray = doubleArrayOf(0.3, 0.6, 0.9),
        gains: DoubleArray = doubleArrayOf(0.5, 0.3, 0.1),
        dryGain: Double = 0.7,
        sampleRate: Int = 44100,
        autoNormalize: Boolean = true
    ): DoubleArray {
        require(delays.size == gains.size) { "延迟时间和增益数组长度必须相同" }
        if (samples.isEmpty()) return DoubleArray(0)

        val totalSamples = samples.size

        // 计算每个延迟对应的样本数
        val delaySamples = IntArray(delays.size)
        for (i in delays.indices) {
            delaySamples[i] = (delays[i] * sampleRate).toInt().coerceAtLeast(0)
        }

        val result = DoubleArray(totalSamples)

        // 处理每个样本
        for (i in 0 until totalSamples) {
            // 获取当前输入样本（干信号）
            val drySample = samples[i]

            // 初始化混合样本为干信号
            var mixedSample = drySample * dryGain

            // 添加每个延迟抽头的信号
            for (tap in delays.indices) {
                val delay = delaySamples[tap]
                val gain = gains[tap]

                if (i >= delay) {
                    val delayedSample = samples[i - delay]
                    mixedSample += delayedSample * gain
                }
            }

            // 将混合结果裁剪到安全范围
            result[i] = mixedSample.coerceIn(-1.0, 1.0)
        }

        // 应用峰值归一化防止削波
        if (autoNormalize) {
            val maxAmplitude = result.maxByOrNull { kotlin.math.abs(it) } ?: 0.0
            val peak = kotlin.math.abs(maxAmplitude)

            if (peak > 1.0) {
                val scaleFactor = 1.0 / peak
                for (i in result.indices) {
                    result[i] *= scaleFactor
                }
            }
        }

        return result
    }

    /**
     * 应用反馈延迟（回声效果）
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @param delayTime 延迟时间(秒)，默认0.3
     * @param feedback 反馈系数(0.0-1.0)，默认0.5
     * @param iterations 迭代次数（回声数量），默认5
     * @param sampleRate 采样率(Hz)，默认44100
     * @param autoNormalize 是否自动归一化，默认true
     * @return 应用反馈延迟后的样本数组
     */
    @JvmStatic
    @JvmOverloads
    fun applyFeedbackDelay(
        samples: DoubleArray,
        delayTime: Double = 0.3,
        feedback: Double = 0.5,
        iterations: Int = 5,
        sampleRate: Int = 44100,
        autoNormalize: Boolean = true
    ): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        val totalSamples = samples.size

        // 计算延迟样本数
        val delaySamples = (delayTime * sampleRate).toInt().coerceAtLeast(0)

        // 扩展输出数组以容纳所有回声
        val extendedSamples = totalSamples + (delaySamples * iterations)
        val result = DoubleArray(extendedSamples)

        // 复制原始信号
        for (i in 0 until totalSamples) {
            result[i] = samples[i]
        }

        // 生成回声
        for (echo in 1..iterations) {
            val echoStart = delaySamples * echo
            val echoGain = Math.pow(feedback, echo.toDouble())

            for (i in 0 until totalSamples) {
                val targetIndex = echoStart + i
                if (targetIndex < extendedSamples) {
                    result[targetIndex] += samples[i] * echoGain
                }
            }
        }

        // 裁剪到安全范围
        for (i in 0 until extendedSamples) {
            result[i] = result[i].coerceIn(-1.0, 1.0)
        }

        // 应用峰值归一化
        if (autoNormalize) {
            val maxAmplitude = result.maxByOrNull { kotlin.math.abs(it) } ?: 0.0
            val peak = kotlin.math.abs(maxAmplitude)

            if (peak > 1.0) {
                val scaleFactor = 1.0 / peak
                for (i in result.indices) {
                    result[i] *= scaleFactor
                }
            }
        }

        return result
    }

    /**
     * 应用振幅调制（AM）效果
     * 用途：创造颤音、震音效果，模拟振动
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @param modFrequency 调制频率(Hz)，默认5.0
     * @param modDepth 调制深度(0.0-1.0)，默认0.5
     * @param sampleRate 采样率(Hz)，默认44100
     * @return 调制后的样本数组
     */
    @JvmStatic
    @JvmOverloads
    fun amplitudeModulation(
        samples: DoubleArray,
        modFrequency: Double = 5.0,
        modDepth: Double = 0.5,
        sampleRate: Double = 44100.0
    ): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        return DoubleArray(samples.size) { i ->
            val time = i.toDouble() / sampleRate
            // 振幅调制：A(t) = 1 + depth * sin(2π * modFreq * t)
            val modulator = 1.0 + modDepth * Math.sin(2.0 * Math.PI * modFrequency * time)
            (samples[i] * modulator).coerceIn(-1.0, 1.0)
        }
    }
}