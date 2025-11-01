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
     * 合成白噪声声音数据
     * @param duration 声音持续时间，单位：秒
     * @param amplitude 幅值，范围：0.0-1.0
     * @param sampleRate 采样率，单位：赫兹（Hz），默认44100Hz
     * @return 合成的音频数据
     */
    @JvmStatic
    @JvmOverloads
    fun whiteNoise(
        duration: Float,
        amplitude: Float = 0.3f,
        sampleRate: Int = 44100
    ): SoundData {
        val samples = (duration * sampleRate).toInt()
        val byteBuffer = ByteBuffer.allocateDirect(samples * 2)

        for (i in 0 until samples) {
            val sample = (amplitude * (random.nextFloat() * 2 - 1) * Short.MAX_VALUE).toInt()
            byteBuffer.putShort(i * 2, sample.toShort())
        }

        byteBuffer.rewind()
        val format = AudioFormat(sampleRate.toFloat(), 16, 1, true, false)
        return SoundData(byteBuffer, format)
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
        // 基础频率的方波（提供核心音调）
        val baseWave = squareWave(duration, baseFrequency, amplitude * 0.7f, 0.5f, sampleRate = sampleRate)

        // 高频成分（提供"嗡嗡"声）
        val highFreq = squareWave(duration, baseFrequency * 2f, amplitude * 0.3f, 0.5f, sampleRate = sampleRate)

        // 一些噪声（模拟电机粗糙度）
        val noise = whiteNoise(duration, amplitude * roughness * 0.002f, sampleRate)

        return mixSounds(listOf(baseWave, highFreq, noise))
    }
}