package cn.solarmoon.spark_core.util.sound

import cn.solarmoon.spark_core.sound.SoundData
import net.minecraft.util.RandomSource

/**
 * 音频合成工具类
 * 提供多种基础波形和音效合成功能
 * 所有方法现在接收和返回DoubleArray（范围[-1.0, 1.0]）
 */
object WaveGenerators {
    @JvmStatic
    val random = RandomSource.create()

    /**
     * 合成正弦波样本
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    @JvmOverloads
    fun sineWave(
        duration: Double,
        frequency: Double,
        amplitude: Double = 0.5,
        phaseOffset: Double = 0.0,
        sampleRate: Int = 44100
    ): DoubleArray {
        val samples = (duration * sampleRate).toInt()
        val result = DoubleArray(samples)

        // 奈奎斯特频率检查
        val nyquistFrequency = sampleRate / 2.0
        if (frequency > nyquistFrequency * 0.9) {
            println("警告: 频率 $frequency Hz 接近奈奎斯特频率 $nyquistFrequency Hz，可能出现混叠")
        }

        // 使用基于时间的相位计算，避免累积误差
        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate
            // 直接计算每个样本的相位，避免累积
            val phase = 2.0 * Math.PI * frequency * time + phaseOffset
            result[i] = amplitude * Math.sin(phase)
        }

        return result
    }

    /**
     * 合成高斯白噪声样本
     * 高斯分布更符合自然噪声特性，听感更柔和
     * @param duration 声音持续时间，单位：秒
     * @param amplitude 幅值，范围：0.0-1.0（控制标准差）
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    @JvmOverloads
    fun gaussianWhiteNoise(
        duration: Double,
        amplitude: Double = 0.3,
        sampleRate: Int = 44100
    ): DoubleArray {
        val samples = (duration * sampleRate).toInt()
        val result = DoubleArray(samples)

        // 使用振幅控制标准差，确保大部分样本在合理范围内
        val stdDev = amplitude * 0.5

        for (i in 0 until samples) {
            // 生成高斯分布随机数（均值为0，标准差为stdDev）
            val gaussianValue = random.nextGaussian() * stdDev
            result[i] = gaussianValue.coerceIn(-1.0, 1.0)
        }

        return result
    }

    /**
     * 白噪声样本（可选高斯或均匀分布）
     * @param duration 声音持续时间，单位：秒
     * @param amplitude 幅值，范围：0.0-1.0（控制标准差）
     * @param useGaussian 是否使用高斯分布，默认true（推荐）
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    @JvmOverloads
    fun whiteNoise(
        duration: Double,
        amplitude: Double = 0.3,
        useGaussian: Boolean = true,
        sampleRate: Int = 44100
    ): DoubleArray {
        return if (useGaussian) {
            gaussianWhiteNoise(duration, amplitude, sampleRate)
        } else {
            val samples = (duration * sampleRate).toInt()
            val result = DoubleArray(samples)

            for (i in 0 until samples) {
                // 均匀分布但进行软化处理
                val uniform = random.nextDouble() * 2 - 1
                // 应用软化曲线，减少极端值
                val softened = Math.signum(uniform) * Math.sqrt(Math.abs(uniform))
                result[i] = amplitude * softened
            }

            result
        }
    }

    /**
     * 合成方波样本
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param dutyCycle 占空比，范围：0.0-1.0，默认0.5（50%）
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    @JvmOverloads
    fun squareWave(
        duration: Double,
        frequency: Double,
        amplitude: Double = 0.5,
        dutyCycle: Double = 0.5,
        phaseOffset: Double = 0.0,
        sampleRate: Int = 44100
    ): DoubleArray {
        require(dutyCycle in 0.0..1.0) { "占空比必须在0.0到1.0之间" }

        val samples = (duration * sampleRate).toInt()
        val result = DoubleArray(samples)

        // 奈奎斯特频率检查
        val nyquistFrequency = sampleRate / 2.0
        if (frequency > nyquistFrequency * 0.9) {
            println("警告: 频率 $frequency Hz 接近奈奎斯特频率 $nyquistFrequency Hz，可能出现混叠")
        }

        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate
            // 考虑相位偏移
            val phase = ((time * frequency + phaseOffset / (2 * Math.PI)) % 1.0)
            result[i] = if (phase < dutyCycle) amplitude else -amplitude
        }

        return result
    }

    /**
     * 合成锯齿波样本
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param rising 是否为上升锯齿波，true=上升，false=下降，默认true
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    @JvmOverloads
    fun sawtoothWave(
        duration: Double,
        frequency: Double,
        amplitude: Double = 0.5,
        phaseOffset: Double = 0.0,
        rising: Boolean = true,
        sampleRate: Int = 44100
    ): DoubleArray {
        val samples = (duration * sampleRate).toInt()
        val result = DoubleArray(samples)

        val nyquistFrequency = sampleRate / 2.0
        if (frequency > nyquistFrequency * 0.9) {
            println("警告: 频率 $frequency Hz 接近奈奎斯特频率 $nyquistFrequency Hz，可能出现混叠")
        }

        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate
            // 统一使用弧度制相位计算
            val phase = (2.0 * Math.PI * frequency * time + phaseOffset) % (2.0 * Math.PI)
            val normalizedPhase = phase / (2.0 * Math.PI)
            result[i] = if (rising) {
                amplitude * (2 * normalizedPhase - 1) // 上升锯齿：从-1到1
            } else {
                amplitude * (1 - 2 * normalizedPhase) // 下降锯齿：从1到-1
            }
        }

        return result
    }

    /**
     * 合成三角波样本
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param symmetry 波形对称性，范围：0.0-1.0，0.5=对称，默认0.5
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    @JvmOverloads
    fun triangleWave(
        duration: Double,
        frequency: Double,
        amplitude: Double = 0.5,
        phaseOffset: Double = 0.0,
        symmetry: Double = 0.5,
        sampleRate: Int = 44100
    ): DoubleArray {
        require(symmetry in 0.0..1.0) { "对称性必须在0.0到1.0之间" }

        val samples = (duration * sampleRate).toInt()
        val result = DoubleArray(samples)

        val nyquistFrequency = sampleRate / 2.0
        if (frequency > nyquistFrequency * 0.9) {
            println("警告: 频率 $frequency Hz 接近奈奎斯特频率 $nyquistFrequency Hz，可能出现混叠")
        }

        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate
            // 统一使用弧度制相位计算
            val phase = (2.0 * Math.PI * frequency * time + phaseOffset) % (2.0 * Math.PI)
            val normalizedPhase = phase / (2.0 * Math.PI)

            val sample = if (symmetry == 0.0) {
                -amplitude
            } else if (symmetry == 1.0) {
                amplitude
            } else if (normalizedPhase < symmetry) {
                amplitude * (2 * normalizedPhase / symmetry - 1)
            } else {
                amplitude * (1 - 2 * (normalizedPhase - symmetry) / (1 - symmetry))
            }
            result[i] = sample
        }

        return result
    }

    /**
     * 合成脉冲波样本
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param pulseWidth 脉冲宽度，范围：0.0-1.0，默认0.1（10%）
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    @JvmOverloads
    fun pulseWave(
        duration: Double,
        frequency: Double,
        amplitude: Double = 0.5,
        pulseWidth: Double = 0.1,
        phaseOffset: Double = 0.0,
        sampleRate: Int = 44100
    ): DoubleArray {
        require(pulseWidth in 0.0..1.0) { "脉冲宽度必须在0.0到1.0之间" }

        val samples = (duration * sampleRate).toInt()
        val result = DoubleArray(samples)

        val nyquistFrequency = sampleRate / 2.0
        if (frequency > nyquistFrequency * 0.9) {
            println("警告: 频率 $frequency Hz 接近奈奎斯特频率 $nyquistFrequency Hz，可能出现混叠")
        }

        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate
            // 统一使用弧度制相位计算
            val phase = (2.0 * Math.PI * frequency * time + phaseOffset) % (2.0 * Math.PI)
            val normalizedPhase = phase / (2.0 * Math.PI)
            result[i] = if (normalizedPhase < pulseWidth) amplitude else 0.0
        }

        return result
    }

    /**
     * 合成调频（FM）样本
     * @param duration 声音持续时间，单位：秒
     * @param carrierFreq 载波频率，单位：赫兹（Hz）
     * @param modulatorFreq 调制频率，单位：赫兹（Hz）
     * @param modulationIndex 调制指数，控制调制深度，默认1.0
     * @param amplitude 幅值，范围：0.0-1.0
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的样本数组，范围[-1.0, 1.0]
     */
    @JvmStatic
    @JvmOverloads
    fun frequencyModulation(
        duration: Double,
        carrierFreq: Double,
        modulatorFreq: Double,
        modulationIndex: Double = 1.0,
        amplitude: Double = 0.5,
        sampleRate: Int = 44100
    ): DoubleArray {
        val samples = (duration * sampleRate).toInt()
        val result = DoubleArray(samples)

        for (i in 0 until samples) {
            val time = i.toDouble() / sampleRate
            // FM合成公式：A * sin(2πfc*t + I*sin(2πfm*t))
            val modulator = Math.sin(2.0 * Math.PI * modulatorFreq * time)
            result[i] = amplitude * Math.sin(2.0 * Math.PI * carrierFreq * time + modulationIndex * modulator)
        }

        return result
    }

}