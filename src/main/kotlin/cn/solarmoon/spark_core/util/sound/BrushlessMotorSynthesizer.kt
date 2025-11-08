package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.sound.SoundData
import java.nio.ByteBuffer
import javax.sound.sampled.AudioFormat

/**
 * 无刷电机音效合成器
 * 基于物理参数合成逼真的无刷电机音效
 */
object BrushlessMotorSynthesizer {

    /**
     * 无刷电机参数配置
     * @param polePairs 极对数
     * @param pwmFrequency PWM载波频率(Hz)，默认8kHz
     * @param maxRPM 最大转速(RPM)，用于归一化
     * @param mechanicalResonanceFreq 机械共振频率(Hz)
     */
    data class MotorConfig(
        val polePairs: Int = 4,
        val pwmFrequency: Double = 8000.0,
        val maxRPM: Double = 20000.0,
        val mechanicalResonanceFreq: Double = 1200.0
    )

    /**
     * 电机运行状态
     * @param rpm 当前转速(RPM)
     * @param load 负载程度(0.0-1.0)
     * @param throttle 油门/功率(0.0-1.0)
     */
    data class MotorState(
        val rpm: Double,
        val load: Double = 0.5,
        val throttle: Double = 0.5
    )

    // 添加随机源用于相位生成
    private val random = java.util.Random()

    /**
     * 合成完整的无刷电机音效
     * @param duration 时长(秒)
     * @param state 电机运行状态
     * @param config 电机配置
     * @param sampleRate 采样率
     */
    @JvmStatic
    @JvmOverloads
    fun synthesizeBrushlessMotor(
        duration: Double,
        state: MotorState,
        config: MotorConfig = MotorConfig(),
        sampleRate: Int = 44100
    ): SoundData {
        // 分层合成
        val electromagneticNoise = synthesizeElectromagneticNoise(duration, state, config, sampleRate)
        val mechanicalNoise = synthesizeMechanicalNoise(duration, state, config, sampleRate)
        val pwmWhine = synthesizePWMWhine(duration, state, config, sampleRate)

        // 混合所有层
        return SoundSynthesizers.mixSounds(
            listOf(
                electromagneticNoise,
                mechanicalNoise,
                pwmWhine
            )
        )
    }

    /**
     * 合成电磁噪音层（核心部分）
     * 使用6k±1次谐波模式 - 改进版解决相位问题
     */
    private fun synthesizeElectromagneticNoise(
        duration: Double,
        state: MotorState,
        config: MotorConfig,
        sampleRate: Int
    ): SoundData {
        val baseFrequency = calculateBaseFrequency(state.rpm, config.polePairs)
        val nyquistFrequency = sampleRate / 2.0

        // 6k±1次谐波模式：1,5,7,11,13,17,19次谐波
        val harmonicComponents = listOf(
            HarmonicComponent(1, 1.0),   // 基波
            HarmonicComponent(5, 0.25),  // 5次谐波
            HarmonicComponent(7, 0.20),  // 7次谐波
            HarmonicComponent(11, 0.15), // 11次谐波
            HarmonicComponent(13, 0.10), // 13次谐波
            HarmonicComponent(17, 0.08), // 17次谐波
            HarmonicComponent(19, 0.04)  // 19次谐波
        )
        val waves = mutableListOf<SoundData>()
        for (component in harmonicComponents) {
            val harmonicFreq = baseFrequency * component.order
            // 抗混叠检查
            if (harmonicFreq >= nyquistFrequency * 0.85) {
                continue // 跳过可能产生混叠的谐波
            }
            waves.add(
                SoundSynthesizers.sineWave(
                    duration,
                    harmonicFreq,
                    calculateHarmonicAmplitude(component.amplitude, state, config),
                    0.0,
                    sampleRate
                )
            )
        }
        return applyHarmonicDistortion(SoundSynthesizers.mixSounds(waves), state.load)
    }

    /**
     * 合成机械噪音层
     */
    private fun synthesizeMechanicalNoise(
        duration: Double,
        state: MotorState,
        config: MotorConfig,
        sampleRate: Int
    ): SoundData {
        // 基础机械噪音（轴承、齿轮等）
        val baseNoise = SoundSynthesizers.gaussianWhiteNoise(
            duration = duration,
            amplitude = state.load * (0.4 + 0.9 * state.rpm / config.maxRPM), // 负载越大机械噪音越大
            sampleRate = sampleRate
        )

        // 应用低通滤波突出机械共振频率
        val filteredNoise = SoundSynthesizers.lowPassFilter(
            soundData = baseNoise,
            cutoff = config.mechanicalResonanceFreq * (state.rpm / config.maxRPM).coerceIn(0.1, 2.0),
            resonance = 0.5
        )

        // 添加振幅调制模拟旋转机械的周期性
        return SoundSynthesizers.amplitudeModulation(
            soundData = filteredNoise,
            modFrequency = calculateBaseFrequency(state.rpm, config.polePairs) * 0.5, // 半频调制
            modDepth = state.load
        )
    }

    /**
     * 合成PWM载波啸叫层
     */
    private fun synthesizePWMWhine(
        duration: Double,
        state: MotorState,
        config: MotorConfig,
        sampleRate: Int
    ): SoundData {
        // PWM啸叫在轻载时更明显
        val pwmAmplitude = 0.15 * (1.0 - state.load * 0.7)

        val pwm = SoundSynthesizers.squareWave(
            duration = duration,
            frequency = config.pwmFrequency,
            amplitude = pwmAmplitude,
            sampleRate = sampleRate
        )
        val harmonic = SoundSynthesizers.squareWave(
            duration = duration,
            frequency = 2 * config.pwmFrequency,
            amplitude = 0.2 * pwmAmplitude,
            sampleRate = sampleRate
        )
        return SoundSynthesizers.mixSounds(
            listOf(
                pwm,
                harmonic
            )
        )
    }

    /**
     * 应用谐波失真
     * 模拟电磁饱和和非线性效应
     */
    private fun applyHarmonicDistortion(
        soundData: SoundData,
        load: Double
    ): SoundData {
        // 简单的软削波失真
        val buffer = soundData.byteBuffer()
        val format = soundData.audioFormat()
        val newBuffer = ByteBuffer.allocateDirect(buffer.capacity())

        val distortionAmount = 0.05 + load * 0.5 // 负载越大失真越明显

        for (i in 0 until buffer.capacity() / 2) {
            var sample = buffer.getShort(i * 2).toDouble() / Short.MAX_VALUE

            // 软削波：tanh风格的软化
            sample = Math.tanh(sample * (1.0 + distortionAmount)) / (1.0 + distortionAmount)

            newBuffer.putShort(i * 2, (sample * Short.MAX_VALUE).toInt().toShort())
        }

        newBuffer.rewind()
        return SoundData(newBuffer, format)
    }

    /**
     * 计算电磁基频
     * 公式：基频 = (RPM / 60) * 极对数
     */
    private fun calculateBaseFrequency(rpm: Double, polePairs: Int): Double {
        return (rpm / 60.0) * polePairs
    }

    /**
     * 计算谐波振幅
     * 考虑负载和转速的影响
     */
    private fun calculateHarmonicAmplitude(
        baseAmplitude: Double,
        state: MotorState,
        config: MotorConfig
    ): Double {
        // 负载越大，电磁噪音越强
        val loadFactor = 0.05 + state.load * 0.95
        return baseAmplitude * loadFactor
    }

    /**
     * 谐波分量数据类
     * @param order 谐波次数
     * @param amplitude 基础振幅
     */
    private data class HarmonicComponent(
        val order: Int,
        val amplitude: Double
    )

    /**
     * 创建动态电机音效序列
     * 用于模拟加速、减速等动态过程
     */
    @JvmStatic
    fun createDynamicMotorSequence(
        duration: Double,
        initialState: MotorState,
        finalState: MotorState,
        config: MotorConfig = MotorConfig(),
        steps: Int = 10,
        sampleRate: Int = 44100
    ): List<SoundData> {
        val sequence = mutableListOf<SoundData>()
        val stepDuration = duration / steps

        for (step in 0 until steps) {
            val progress = step.toDouble() / (steps - 1)
            val currentRpm = initialState.rpm + (finalState.rpm - initialState.rpm) * progress
            val currentLoad = initialState.load + (finalState.load - initialState.load) * progress
            val currentThrottle = initialState.throttle + (finalState.throttle - initialState.throttle) * progress

            val currentState = MotorState(
                rpm = currentRpm,
                load = currentLoad,
                throttle = currentThrottle
            )

            val stepSound = synthesizeBrushlessMotor(
                duration = stepDuration,
                state = currentState,
                config = config,
                sampleRate = sampleRate
            )

            sequence.add(stepSound)
        }

        return sequence
    }

    /**
     * 快速创建常见类型无刷电机的预设配置
     */
    object MotorPresets {
        @JvmStatic
        fun droneMotor(): MotorConfig {
            return MotorConfig(
                polePairs = 7,
                pwmFrequency = 16000.0, // 高频PWM减少可闻啸叫
                maxRPM = 25000.0,
                mechanicalResonanceFreq = 1500.0
            )
        }

        @JvmStatic
        fun evMotor(): MotorConfig {
            return MotorConfig(
                polePairs = 4,
                pwmFrequency = 10000.0,
                maxRPM = 15000.0,
                mechanicalResonanceFreq = 800.0
            )
        }

        @JvmStatic
        fun industrialMotor(): MotorConfig {
            return MotorConfig(
                polePairs = 6,
                pwmFrequency = 5000.0,
                maxRPM = 8000.0,
                mechanicalResonanceFreq = 600.0
            )
        }
    }
}