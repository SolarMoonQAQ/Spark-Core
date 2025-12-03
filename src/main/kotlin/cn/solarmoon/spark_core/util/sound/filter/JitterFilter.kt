package cn.solarmoon.spark_core.util.sound.filter

import kotlin.math.floor
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.math.PI

/**
 * 抖动滤波器 - 为音频添加时间抖动效果
 * 用途：模拟磁带机/留声机的不稳定转速，创造复古、有机的感觉
 */
class JitterFilter {
    /**
     * 抖动参数配置
     * @param jitterAmount 抖动强度 (0.0-1.0)，控制最大时间偏移量占音频长度的比例
     * @param jitterFrequency 抖动频率 (Hz)，控制抖动变化的快慢
     * @param noiseCutoff 噪声截止频率 (Hz)，用于平滑抖动信号
     * @param randomSeed 随机种子，用于可重复的结果
     */
    data class JitterParams(
        val jitterAmount: Double = 0.01,        // 1%的抖动
        val jitterFrequency: Double = 2.0,      // 2Hz的抖动频率
        val noiseCutoff: Double = 2000.0,       // 2000Hz截止频率
        val randomSeed: Long = System.currentTimeMillis()
    )

    /**
     * 应用抖动效果到样本数组
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @param params 抖动参数
     * @param sampleRate 采样率，默认44100
     * @return 添加抖动效果后的样本数组
     */
    fun apply(samples: DoubleArray, params: JitterParams = JitterParams(), sampleRate: Double = 44100.0): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        val totalSamples = samples.size

        // 生成平滑的抖动信号（使用低通滤波的白噪声）
        val jitterSignal = generateJitterSignal(totalSamples, sampleRate, params)

        val result = DoubleArray(totalSamples)

        // 应用抖动效果
        for (i in 0 until totalSamples) {
            // 计算抖动后的采样位置
            val jitterOffset = jitterSignal[i] * params.jitterAmount * totalSamples
            val jitteredIndex = i.toDouble() + jitterOffset

            // 使用线性插值获取抖动后的采样值
            result[i] = interpolateSample(samples, jitteredIndex, totalSamples)
        }

        return result
    }

    /**
     * 生成平滑的抖动信号
     * @param totalSamples 总采样数
     * @param sampleRate 采样率
     * @param params 抖动参数
     * @return 抖动信号数组，范围[-1.0, 1.0]
     */
    private fun generateJitterSignal(
        totalSamples: Int,
        sampleRate: Double,
        params: JitterParams
    ): DoubleArray {
        // 生成基频抖动（正弦波）
        val baseJitter = DoubleArray(totalSamples)
        if (params.jitterFrequency > 0) {
            for (i in 0 until totalSamples) {
                val time = i.toDouble() / sampleRate
                baseJitter[i] = sin(2.0 * PI * params.jitterFrequency * time)
            }
        }

        // 生成随机抖动（带限白噪声）
        val randomJitter = generateBandLimitedNoise(totalSamples, sampleRate, params)

        // 混合基频抖动和随机抖动
        val mixedJitter = DoubleArray(totalSamples)
        for (i in 0 until totalSamples) {
            // 基频抖动提供规律性，随机抖动提供不规则性
            mixedJitter[i] = baseJitter[i] * 0.3 + randomJitter[i] * 0.7
        }

        // 对混合后的信号进行低通滤波，使其平滑
        return lowPassFilterSignal(mixedJitter, sampleRate, params.noiseCutoff)
    }

    /**
     * 生成带限白噪声（在截止频率内）
     * @param totalSamples 总采样数
     * @param sampleRate 采样率
     * @param params 抖动参数
     * @return 带限噪声信号
     */
    private fun generateBandLimitedNoise(
        totalSamples: Int,
        sampleRate: Double,
        params: JitterParams
    ): DoubleArray {
        // 生成白噪声
        val noise = DoubleArray(totalSamples)
        val random = java.util.Random(params.randomSeed)

        for (i in 0 until totalSamples) {
            noise[i] = random.nextDouble() * 2 - 1  // 范围[-1, 1]
        }

        // 应用低通滤波器，限制噪声带宽
        return lowPassFilterSignal(noise, sampleRate, params.noiseCutoff)
    }

    /**
     * 应用简单的IIR低通滤波器
     * @param signal 输入信号
     * @param sampleRate 采样率
     * @param cutoff 截止频率
     * @return 滤波后的信号
     */
    private fun lowPassFilterSignal(
        signal: DoubleArray,
        sampleRate: Double,
        cutoff: Double
    ): DoubleArray {
        if (cutoff <= 0) return signal.copyOf()

        val filtered = DoubleArray(signal.size)
        val dt = 1.0 / sampleRate
        val RC = 1.0 / (2.0 * PI * cutoff)
        val alpha = dt / (RC + dt)

        var prevOutput = 0.0
        for (i in signal.indices) {
            val output = prevOutput + alpha * (signal[i] - prevOutput)
            filtered[i] = output
            prevOutput = output
        }

        return filtered
    }

    /**
     * 使用线性插值获取指定位置的采样值
     * @param samples 样本数组
     * @param index 插值位置（可能是小数）
     * @param totalSamples 总采样数
     * @return 插值后的采样值
     */
    private fun interpolateSample(
        samples: DoubleArray,
        index: Double,
        totalSamples: Int
    ): Double {
        // 确保索引在有效范围内
        val clampedIndex = index.coerceIn(0.0, (totalSamples - 1).toDouble())

        // 计算前后索引
        val floorIndex = floor(clampedIndex).toInt()
        val ceilIndex = ceil(clampedIndex).toInt().coerceAtMost(totalSamples - 1)

        // 获取前后采样值
        val floorSample = if (floorIndex < totalSamples) {
            samples[floorIndex]
        } else 0.0

        val ceilSample = if (ceilIndex < totalSamples && ceilIndex != floorIndex) {
            samples[ceilIndex]
        } else floorSample

        // 计算插值权重
        val fraction = clampedIndex - floorIndex

        // 线性插值
        return floorSample * (1.0 - fraction) + ceilSample * fraction
    }

    companion object {
        /**
         * 便捷的静态方法
         */
        @JvmStatic
        @JvmOverloads
        fun applyJitter(
            samples: DoubleArray,
            jitterAmount: Double = 0.01,
            jitterFrequency: Double = 2.0,
            noiseCutoff: Double = 20.0,
            randomSeed: Long = System.currentTimeMillis(),
            sampleRate: Double = 44100.0
        ): DoubleArray {
            val filter = JitterFilter()
            val params = JitterParams(
                jitterAmount = jitterAmount,
                jitterFrequency = jitterFrequency,
                noiseCutoff = noiseCutoff,
                randomSeed = randomSeed
            )
            return filter.apply(samples, params, sampleRate)
        }
    }
}