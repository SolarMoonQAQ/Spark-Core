package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.sound.SoundData
import net.minecraft.util.RandomSource
import java.nio.ByteBuffer
import javax.sound.sampled.AudioFormat

/**
 * 音频合成工具类
 * 提供多种基础波形和音效合成功能
 */
object SoundSynthesizers {
    @JvmStatic
    val random = RandomSource.create()

    /**
     * ADSR包络参数
     * @param attack 起音时间(秒)
     * @param decay 衰减时间(秒)
     * @param sustain 持续电平(0.0-1.0)
     * @param release 释音时间(秒)
     */
    data class ADSREnvelope(
        val attack: Float = 0.1f,    // 起音时间(秒)
        val decay: Float = 0.1f,     // 衰减时间(秒)
        val sustain: Float = 0.7f,   // 持续电平(0.0-1.0)
        val release: Float = 0.2f    // 释音时间(秒)
    )

    /**
     * 应用ADSR包络到声音数据（改进版，自动计算时长）
     * @param soundData 声音数据
     * @param envelope ADSR包络参数
     * @return 应用包络后的声音数据
     */
    @JvmStatic
    fun applyADSREnvelope(
        soundData: SoundData,
        envelope: ADSREnvelope
    ): SoundData {
        val buffer = soundData.byteBuffer()
        val format = soundData.audioFormat()
        val sampleRate = format.sampleRate.toInt()
        val totalSamples = buffer.capacity() / (format.sampleSizeInBits / 8)

        // 自动计算总时长（秒）
        val duration = totalSamples.toFloat() / sampleRate

        val newBuffer = ByteBuffer.allocateDirect(buffer.capacity())

        val attackSamples = (envelope.attack * sampleRate).toInt()
        val decaySamples = (envelope.decay * sampleRate).toInt()
        val releaseSamples = (envelope.release * sampleRate).toInt()
        val sustainSamples = totalSamples - attackSamples - decaySamples - releaseSamples

        // 确保样本数合理
        val actualSustainSamples = sustainSamples.coerceAtLeast(0)
        val actualTotalSamples = (attackSamples + decaySamples + actualSustainSamples + releaseSamples).coerceAtMost(totalSamples)

        for (i in 0 until actualTotalSamples) {
            val envelopeValue = when {
                i < attackSamples -> i.toFloat() / attackSamples // 线性起音
                i < attackSamples + decaySamples -> {
                    val decayProgress = (i - attackSamples).toFloat() / decaySamples
                    1.0f - (1.0f - envelope.sustain) * decayProgress // 衰减到持续电平
                }
                i < attackSamples + decaySamples + actualSustainSamples -> envelope.sustain
                else -> {
                    val releaseProgress = (i - (attackSamples + decaySamples + actualSustainSamples)).toFloat() / releaseSamples
                    envelope.sustain * (1.0f - releaseProgress) // 释音
                }
            }

            val originalSample = buffer.getShort(i * 2)
            val newSample = (originalSample * envelopeValue).toInt().toShort()
            newBuffer.putShort(i * 2, newSample)
        }

        // 填充剩余样本（如果有）
        for (i in actualTotalSamples until totalSamples) {
            newBuffer.putShort(i * 2, 0)
        }

        newBuffer.rewind()
        return SoundData(newBuffer, format)
    }

    /**
     * 应用低通滤波器到声音数据
     * 用途：模拟闷响、远距离声音，去除高频刺耳声
     * @param soundData 声音数据
     * @param cutoff 截止频率(Hz)
     * @param resonance 共振强度(0.0-1.0)
     * @return 滤波后的声音数据
     */
    @JvmStatic
    @JvmOverloads
    fun lowPassFilter(
        soundData: SoundData,
        cutoff: Float = 1000f,
        resonance: Float = 0.5f
    ): SoundData {
        val buffer = soundData.byteBuffer()
        val format = soundData.audioFormat()
        val sampleRate = format.sampleRate.toFloat()
        val newBuffer = ByteBuffer.allocateDirect(buffer.capacity())

        val dt = 1.0f / sampleRate
        val RC = 1.0f / (2.0f * Math.PI.toFloat() * cutoff)
        val alpha = dt / (RC + dt)

        var prevOutput = 0.0f

        for (i in 0 until buffer.capacity() / 2) {
            val input = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE
            val output = prevOutput + alpha * (input - prevOutput)
            prevOutput = output

            val resonated = output * (1.0f + resonance * 0.1f).coerceAtMost(1.5f)
            val finalOutput = resonated.coerceIn(-1.0f, 1.0f)

            newBuffer.putShort(i * 2, (finalOutput * Short.MAX_VALUE).toInt().toShort())
        }

        newBuffer.rewind()
        return SoundData(newBuffer, format)
    }

    /**
     * 应用高通滤波器到声音数据
     * 用途：去除低频嗡嗡声，创造薄脆音色
     * @param soundData 声音数据
     * @param cutoff 截止频率(Hz)
     * @return 滤波后的声音数据
     */
    @JvmStatic
    @JvmOverloads
    fun highPassFilter(
        soundData: SoundData,
        cutoff: Float = 200f
    ): SoundData {
        val buffer = soundData.byteBuffer()
        val format = soundData.audioFormat()
        val sampleRate = format.sampleRate.toFloat()
        val newBuffer = ByteBuffer.allocateDirect(buffer.capacity())

        val rc = 1.0f / (2.0f * Math.PI.toFloat() * cutoff)
        val dt = 1.0f / sampleRate
        val alpha = rc / (rc + dt)

        var prevInput = 0.0f
        var prevOutput = 0.0f

        for (i in 0 until buffer.capacity() / 2) {
            val input = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE
            val output = alpha * (prevOutput + input - prevInput)

            prevInput = input
            prevOutput = output

            newBuffer.putShort(i * 2, (output * Short.MAX_VALUE).toInt().toShort())
        }

        newBuffer.rewind()
        return SoundData(newBuffer, format)
    }

    /**
     * 应用带通滤波器到声音数据
     * 用途：突出特定频段，模拟电话音、特定共振
     * @param soundData 声音数据
     * @param centerFreq 中心频率(Hz)
     * @param bandwidth 带宽(Hz)
     * @return 滤波后的声音数据
     */
    @JvmStatic
    @JvmOverloads
    fun bandPassFilter(
        soundData: SoundData,
        centerFreq: Float = 1000f,
        bandwidth: Float = 500f
    ): SoundData {
        val buffer = soundData.byteBuffer()
        val format = soundData.audioFormat()
        val sampleRate = format.sampleRate.toFloat()
        val newBuffer = ByteBuffer.allocateDirect(buffer.capacity())

        val R = 1.0f - 3.0f * bandwidth / sampleRate
        val cosTheta = (2.0f * R * Math.cos(2.0f * Math.PI * centerFreq / sampleRate)) / (1.0f + R * R)
        val alpha = (1.0f - R * R) * Math.sin(2.0f * Math.PI * centerFreq / sampleRate) / 2.0f

        var prevInput1 = 0.0f
        var prevInput2 = 0.0f
        var prevOutput1 = 0.0f
        var prevOutput2 = 0.0f

        for (i in 0 until buffer.capacity() / 2) {
            val input = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE

            val output = alpha * (input - prevInput2) +
                    2.0f * R * cosTheta * prevOutput1 -
                    R * R * prevOutput2

            prevInput2 = prevInput1
            prevInput1 = input
            prevOutput2 = prevOutput1
            prevOutput1 = output.toFloat()

            newBuffer.putShort(i * 2, (output * Short.MAX_VALUE).toInt().toShort())
        }

        newBuffer.rewind()
        return SoundData(newBuffer, format)
    }

    /**
     * 合成正弦波声音数据
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的音频数据
     */
    @JvmStatic
    @JvmOverloads
    fun sineWave(
        duration: Float,
        frequency: Float,
        amplitude: Float = 0.5f,
        phaseOffset: Float = 0.0f,
        sampleRate: Int = 44100
    ): SoundData {
        val samples = (duration * sampleRate).toInt()
        val byteBuffer = ByteBuffer.allocateDirect(samples * 2) // 16-bit mono

        for (i in 0 until samples) {
            val time = i.toFloat() / sampleRate
            val sample = (amplitude * Math.sin(2.0 * Math.PI * frequency * time + phaseOffset).toFloat() * Short.MAX_VALUE).toInt()
            byteBuffer.putShort(i * 2, sample.toShort())
        }

        byteBuffer.rewind()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
    }

    /**
     * 合成高斯白噪声声音数据
     * 高斯分布更符合自然噪声特性，听感更柔和
     * @param duration 声音持续时间，单位：秒
     * @param amplitude 幅值，范围：0.0-1.0（控制标准差）
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的音频数据
     */
    @JvmStatic
    @JvmOverloads
    fun gaussianWhiteNoise(
        duration: Float,
        amplitude: Float = 0.3f,
        sampleRate: Int = 44100
    ): SoundData {
        val samples = (duration * sampleRate).toInt()
        val byteBuffer = ByteBuffer.allocateDirect(samples * 2)

        // 使用振幅控制标准差，确保大部分样本在合理范围内
        val stdDev = amplitude * 0.5f

        for (i in 0 until samples) {
            // 生成高斯分布随机数（均值为0，标准差为stdDev）
            val gaussianValue = (random.nextGaussian() * stdDev).toFloat()

            // 裁剪到安全范围并转换为short
            val clippedValue = gaussianValue.coerceIn(-1.0f, 1.0f)
            val sample = (clippedValue * Short.MAX_VALUE).toInt()
            byteBuffer.putShort(i * 2, sample.toShort())
        }

        byteBuffer.rewind()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
    }

    /**
     * 白噪声（可选高斯或均匀分布）
     * @param duration 声音持续时间，单位：秒
     * @param amplitude 幅值，范围：0.0-1.0（控制标准差）
     * @param useGaussian 是否使用高斯分布，默认true（推荐）
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     */
    @JvmStatic
    @JvmOverloads
    fun whiteNoise(
        duration: Float,
        amplitude: Float = 0.3f,
        useGaussian: Boolean = true,
        sampleRate: Int = 44100
    ): SoundData {
        return if (useGaussian) {
            gaussianWhiteNoise(duration, amplitude, sampleRate)
        } else {
            // 原均匀分布版本，但添加软化处理
            val samples = (duration * sampleRate).toInt()
            val byteBuffer = ByteBuffer.allocateDirect(samples * 2)

            for (i in 0 until samples) {
                // 均匀分布但进行软化处理
                val uniform = random.nextFloat() * 2 - 1
                // 应用软化曲线，减少极端值
                val softened = Math.signum(uniform) * Math.sqrt(Math.abs(uniform).toDouble()).toFloat()
                val sample = (amplitude * softened * Short.MAX_VALUE).toInt()
                byteBuffer.putShort(i * 2, sample.toShort())
            }

            byteBuffer.rewind()
            val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
            SoundData(byteBuffer, format)
        }
    }

    /**
     * 合成方波声音数据
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param dutyCycle 占空比，范围：0.0-1.0，默认0.5（50%）
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的音频数据
     */
    @JvmStatic
    @JvmOverloads
    fun squareWave(
        duration: Float,
        frequency: Float,
        amplitude: Float = 0.5f,
        dutyCycle: Float = 0.5f,
        phaseOffset: Float = 0.0f,
        sampleRate: Int = 44100
    ): SoundData {
        require(dutyCycle in 0.0f..1.0f) { "占空比必须在0.0到1.0之间" }

        val samples = (duration * sampleRate).toInt()
        val byteBuffer = ByteBuffer.allocateDirect(samples * 2)

        for (i in 0 until samples) {
            val time = i.toFloat() / sampleRate
            val period = 1.0f / frequency
            // 考虑相位偏移
            val phase = ((time * frequency + phaseOffset / (2 * Math.PI.toFloat())) % 1.0f).toFloat()
            val sample = if (phase < dutyCycle) amplitude else -amplitude
            val sampleValue = (sample * Short.MAX_VALUE).toInt()
            byteBuffer.putShort(i * 2, sampleValue.toShort())
        }

        byteBuffer.rewind()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
    }

    /**
     * 合成锯齿波声音数据
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param rising 是否为上升锯齿波，true=上升，false=下降，默认true
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的音频数据
     */
    @JvmStatic
    @JvmOverloads
    fun sawtoothWave(
        duration: Float,
        frequency: Float,
        amplitude: Float = 0.5f,
        phaseOffset: Float = 0.0f,
        rising: Boolean = true,
        sampleRate: Int = 44100
    ): SoundData {
        val samples = (duration * sampleRate).toInt()
        val byteBuffer = ByteBuffer.allocateDirect(samples * 2)

        for (i in 0 until samples) {
            val time = i.toFloat() / sampleRate
            // 考虑相位偏移
            val phase = ((time * frequency + phaseOffset / (2 * Math.PI.toFloat())) % 1.0f).toFloat()
            val sample = if (rising) {
                amplitude * (2 * phase - 1) // 上升锯齿：从-1到1
            } else {
                amplitude * (1 - 2 * phase) // 下降锯齿：从1到-1
            }
            val sampleValue = (sample * Short.MAX_VALUE).toInt()
            byteBuffer.putShort(i * 2, sampleValue.toShort())
        }

        byteBuffer.rewind()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
    }

    /**
     * 合成三角波声音数据
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param symmetry 波形对称性，范围：0.0-1.0，0.5=对称，默认0.5
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的音频数据
     */
    @JvmStatic
    @JvmOverloads
    fun triangleWave(
        duration: Float,
        frequency: Float,
        amplitude: Float = 0.5f,
        phaseOffset: Float = 0.0f,
        symmetry: Float = 0.5f,
        sampleRate: Int = 44100
    ): SoundData {
        require(symmetry in 0.0f..1.0f) { "对称性必须在0.0到1.0之间" }

        val samples = (duration * sampleRate).toInt()
        val byteBuffer = ByteBuffer.allocateDirect(samples * 2)

        for (i in 0 until samples) {
            val time = i.toFloat() / sampleRate
            // 考虑相位偏移
            val phase = ((time * frequency + phaseOffset / (2 * Math.PI.toFloat())) % 1.0f).toFloat()

            val sample = if (symmetry == 0f) {
                -amplitude
            } else if (symmetry == 1f) {
                amplitude
            } else if (phase < symmetry) {
                amplitude * (2 * phase / symmetry - 1)
            } else {
                amplitude * (1 - 2 * (phase - symmetry) / (1 - symmetry))
            }
            val sampleValue = (sample * Short.MAX_VALUE).toInt()
            byteBuffer.putShort(i * 2, sampleValue.toShort())
        }

        byteBuffer.rewind()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
    }

    /**
     * 合成脉冲波声音数据
     * @param duration 声音持续时间，单位：秒
     * @param frequency 声音频率，单位：赫兹（Hz）
     * @param amplitude 幅值，范围：0.0-1.0
     * @param pulseWidth 脉冲宽度，范围：0.0-1.0，默认0.1（10%）
     * @param phaseOffset 相位偏移，单位：弧度（radians），默认0.0
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的音频数据
     */
    @JvmStatic
    @JvmOverloads
    fun pulseWave(
        duration: Float,
        frequency: Float,
        amplitude: Float = 0.5f,
        pulseWidth: Float = 0.1f,
        phaseOffset: Float = 0.0f,
        sampleRate: Int = 44100
    ): SoundData {
        require(pulseWidth in 0.0f..1.0f) { "脉冲宽度必须在0.0到1.0之间" }

        val samples = (duration * sampleRate).toInt()
        val byteBuffer = ByteBuffer.allocateDirect(samples * 2)

        for (i in 0 until samples) {
            val time = i.toFloat() / sampleRate
            // 考虑相位偏移
            val phase = ((time * frequency + phaseOffset / (2 * Math.PI.toFloat())) % 1.0f).toFloat()
            val sample = if (phase < pulseWidth) amplitude else 0.0f
            val sampleValue = (sample * Short.MAX_VALUE).toInt()
            byteBuffer.putShort(i * 2, sampleValue.toShort())
        }

        byteBuffer.rewind()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
    }

    /**
     * 合成调频（FM）声音数据
     * @param duration 声音持续时间，单位：秒
     * @param carrierFreq 载波频率，单位：赫兹（Hz）
     * @param modulatorFreq 调制频率，单位：赫兹（Hz）
     * @param modulationIndex 调制指数，控制调制深度，默认1.0
     * @param amplitude 幅值，范围：0.0-1.0
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的音频数据
     */
    @JvmStatic
    @JvmOverloads
    fun frequencyModulation(
        duration: Float,
        carrierFreq: Float,
        modulatorFreq: Float,
        modulationIndex: Float = 1.0f,
        amplitude: Float = 0.5f,
        sampleRate: Int = 44100
    ): SoundData {
        val samples = (duration * sampleRate).toInt()
        val byteBuffer = ByteBuffer.allocateDirect(samples * 2)

        for (i in 0 until samples) {
            val time = i.toFloat() / sampleRate
            // FM合成公式：A * sin(2πfc*t + I*sin(2πfm*t))
            val modulator = Math.sin(2.0 * Math.PI * modulatorFreq * time).toFloat()
            val sample = amplitude * Math.sin(2.0 * Math.PI * carrierFreq * time + modulationIndex * modulator).toFloat()
            val sampleValue = (sample * Short.MAX_VALUE).toInt()
            byteBuffer.putShort(i * 2, sampleValue.toShort())
        }

        byteBuffer.rewind()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
    }

    /**
     * 应用振幅调制（AM）效果
     * 用途：创造颤音、震音效果，模拟振动
     * @param soundData 声音数据
     * @param modFrequency 调制频率(Hz)
     * @param modDepth 调制深度(0.0-1.0)
     * @return 调制后的声音数据
     */
    @JvmStatic
    fun amplitudeModulation(
        soundData: SoundData,
        modFrequency: Float = 5.0f,
        modDepth: Float = 0.5f
    ): SoundData {
        val buffer = soundData.byteBuffer()
        val format = soundData.audioFormat()
        val sampleRate = format.sampleRate.toFloat()
        val newBuffer = ByteBuffer.allocateDirect(buffer.capacity())

        for (i in 0 until buffer.capacity() / 2) {
            val time = i.toFloat() / sampleRate
            val originalSample = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE

            // 振幅调制：A(t) = 1 + depth * sin(2π * modFreq * t)
            val modulator = 1.0f + modDepth * Math.sin(2.0f * Math.PI * modFrequency * time).toFloat()
            val modulatedSample = originalSample * modulator

            newBuffer.putShort(i * 2, (modulatedSample.coerceIn(-1.0f, 1.0f) * Short.MAX_VALUE).toInt().toShort())
        }

        newBuffer.rewind()
        return SoundData(newBuffer, format)
    }

    /**
     * 混合多个声音数据
     */
    @JvmStatic
    fun mixSounds(sounds: List<SoundData>): SoundData {
        require(sounds.isNotEmpty()) { "至少需要一个声音数据" }

        val format = sounds.first().audioFormat()
        val maxSamples = sounds.maxOf { it.byteBuffer().capacity() / 2 }

        val mixedBuffer = ByteBuffer.allocateDirect(maxSamples)

        for (i in 0 until maxSamples / 2) {
            var mixedSample = 0.0f

            for (sound in sounds) {
                val buffer = sound.byteBuffer()
                if (i * 2 < buffer.capacity()) {
                    val sample = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE
                    mixedSample += sample
                }
            }

            mixedSample = mixedSample / sounds.size.coerceAtLeast(1)
            val finalSample = (mixedSample * Short.MAX_VALUE).toInt().toShort()
            mixedBuffer.putShort(i * 2, finalSample)
        }

        mixedBuffer.rewind()
        return SoundData(mixedBuffer, format)
    }

    /**
     * 合成基础电机声音
     */
    @JvmStatic
    @JvmOverloads
    fun motorSound(
        duration: Float,
        baseFrequency: Float,
        roughness: Float = 0.1f, // 粗糙度控制
        amplitude: Float = 0.5f,
        sampleRate: Int = 44100
    ): SoundData {
        // 基础频率的方波
        val baseWave = squareWave(duration, baseFrequency, amplitude * 0.7f, 0.5f, sampleRate = sampleRate)

        // 高频成分
        val highFreq = squareWave(duration, baseFrequency * 2f, amplitude * 0.3f, 0.5f, sampleRate = sampleRate)

        // 使用高斯噪声（更自然的粗糙度）
        val noise = gaussianWhiteNoise(duration, amplitude * roughness * 0.05f, sampleRate)

        return mixSounds(listOf(baseWave, highFreq, noise))
    }

}