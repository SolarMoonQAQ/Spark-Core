package cn.solarmoon.spark_core.util.sound

import cn.solarmoon.spark_core.util.sound.filter.MonoFilter

/**
 * 单声道音频效果器
 * 专门在时域处理单声道音频波形数据
 */
object WaveEffects {

    /**
     * 应用ADSR包络到波形样本
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @param attack 起音时间(秒)
     * @param decay 衰减时间(秒)
     * @param sustain 持续电平(0.0-1.0)
     * @param release 释音时间(秒)
     * @param sampleRate 采样率(Hz)，默认44100
     * @return 应用包络后的样本数组
     */
    @JvmStatic
    @JvmOverloads
    fun applyADSR(
        samples: DoubleArray,
        attack: Double = 0.1,    // 起音时间(秒)
        decay: Double = 0.1,     // 衰减时间(秒)
        sustain: Double = 0.7,   // 持续电平(0.0-1.0)
        release: Double = 0.2,    // 释音时间(秒)
        sampleRate: Int = 44100
    ): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        val totalSamples = samples.size
        val result = DoubleArray(totalSamples)

        val attackSamples = (attack * sampleRate).toInt().coerceAtLeast(1)
        val decaySamples = (decay * sampleRate).toInt().coerceAtLeast(1)
        val releaseSamples = (release * sampleRate).toInt().coerceAtLeast(1)
        val sustainSamples = (totalSamples - attackSamples - decaySamples - releaseSamples).coerceAtLeast(0)

        val actualTotalSamples = (attackSamples + decaySamples + sustainSamples + releaseSamples).coerceAtMost(totalSamples)

        for (i in 0 until actualTotalSamples) {
            val envelopeValue = when {
                i < attackSamples -> i.toDouble() / attackSamples
                i < attackSamples + decaySamples -> {
                    val decayProgress = (i - attackSamples).toDouble() / decaySamples
                    1.0 - (1.0 - sustain) * decayProgress
                }
                i < attackSamples + decaySamples + sustainSamples -> sustain
                else -> {
                    val releaseProgress = (i - (attackSamples + decaySamples + sustainSamples)).toDouble() / releaseSamples
                    sustain * (1.0 - releaseProgress)
                }
            }

            result[i] = samples[i] * envelopeValue
        }

        // 填充剩余样本为0
        for (i in actualTotalSamples until totalSamples) {
            result[i] = 0.0
        }

        return result
    }


    /**
     * 音效循环合成器 - 用于高频率循环播放单个音效
     * @param sourceSamples 源音效样本数组，范围[-1.0, 1.0]
     * @param loopFrequency 循环频率(Hz)
     * @param duration 总时长(秒)
     * @param overlap 是否允许音效重叠(用于高频率情况)，默认true
     * @param sampleRate 采样率(Hz)，默认44100
     * @param autoNormalize 是否自动归一化，默认true
     * @return 循环合成的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    @JvmOverloads
    fun loop(
        sourceSamples: DoubleArray,
        loopFrequency: Double,
        duration: Double,
        overlap: Boolean = true,
        sampleRate: Int = 44100,
        autoNormalize: Boolean = true
    ): DoubleArray {
        require(loopFrequency > 0) { "Loop frequency must be positive" }
        require(duration > 0) { "Duration must be positive" }
        require(sampleRate > 0) { "Sample rate must be positive" }

        val sourceSamplesCount = sourceSamples.size

        // 计算循环间隔(采样数)
        val intervalSamples = (sampleRate / loopFrequency).toInt()
        require(intervalSamples > 0) { "Interval samples must be positive" }

        // 如果源音效比间隔长且不允许重叠，则截断
        val effectiveSourceSamples = if (!overlap && sourceSamplesCount > intervalSamples) {
            intervalSamples
        } else {
            sourceSamplesCount
        }

        val totalSamples = (duration * sampleRate).toInt()
        require(totalSamples > 0) { "Total samples must be positive" }

        // 使用Double数组进行计算，避免溢出
        val mixedSamples = DoubleArray(totalSamples) { 0.0 }

        // 计算可以放置的音效数量
        val numLoops = if (overlap) {
            Math.ceil(duration * loopFrequency).toInt().coerceAtLeast(1)
        } else {
            val maxLoops = ((totalSamples - effectiveSourceSamples) / intervalSamples + 1).coerceAtLeast(1)
            maxLoops.coerceAtMost((duration * loopFrequency).toInt() + 1)
        }

        // 放置音效实例到Double数组
        for (loopIndex in 0 until numLoops) {
            val startSample = (loopIndex * intervalSamples).coerceAtMost(
                (totalSamples - effectiveSourceSamples).coerceAtLeast(0)
            )

            for (i in 0 until effectiveSourceSamples) {
                val targetIndex = startSample + i
                if (targetIndex < totalSamples) {
                    mixedSamples[targetIndex] += sourceSamples[i]
                }
            }
        }

        // 峰值归一化
        if (autoNormalize) {
            // 找到最大绝对值
            val maxAmplitude = mixedSamples.maxByOrNull { Math.abs(it) } ?: 0.0
            val peak = Math.abs(maxAmplitude)

            if (peak > 0.0) {
                val scaleFactor = if (peak > 1.0) 1.0 / peak else 1.0

                for (i in mixedSamples.indices) {
                    mixedSamples[i] *= scaleFactor
                }
            }
        } else {
            // 不进行归一化，直接裁剪到有效范围
            for (i in mixedSamples.indices) {
                mixedSamples[i] = mixedSamples[i].coerceIn(-1.0, 1.0)
            }
        }

        return mixedSamples
    }

    /**
     * 混合多个样本数组（使用峰值归一化防止削波）
     * @param samplesList 样本数组列表，每个数组范围[-1.0, 1.0]
     * @param autoNormalize 是否自动归一化，默认true
     * @return 混合后的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    fun mixSamples(samplesList: List<DoubleArray>, autoNormalize: Boolean = true): DoubleArray {
        require(samplesList.isNotEmpty()) { "至少需要一个样本数组" }

        // 找到最长的数组长度
        val maxLength = samplesList.maxOf { it.size }

        // 将所有样本混合到Double数组
        val mixedSamples = DoubleArray(maxLength) { 0.0 }

        for (samples in samplesList) {
            for (i in samples.indices) {
                mixedSamples[i] += samples[i]
            }
        }

        // 应用DC滤波器以消除直流偏移
        val filteredSample = MonoFilter.dcFilter(mixedSamples)

        // 峰值归一化
        if (autoNormalize) {
            val peak = filteredSample.maxByOrNull { Math.abs(it) } ?: 0.0
            val scaleFactor = if (Math.abs(peak) > 1.0) 1.0 / Math.abs(peak) else 1.0

            for (i in filteredSample.indices) {
                filteredSample[i] *= scaleFactor
            }
        } else {
            // 直接裁剪
            for (i in filteredSample.indices) {
                filteredSample[i] = filteredSample[i].coerceIn(-1.0, 1.0)
            }
        }

        return filteredSample
    }
}