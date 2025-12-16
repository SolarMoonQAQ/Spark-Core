package cn.solarmoon.spark_core.util.sound.filter

import cn.solarmoon.spark_core.util.SoundHelper
import cn.solarmoon.spark_core.util.sound.WaveGenerators
import cn.solarmoon.spark_core.util.toSoundData
import java.io.InputStream
import java.io.ByteArrayOutputStream
import javax.sound.sampled.AudioSystem

/**
 * 卷积滤波器类
 * 用于应用冲激响应实现混响、延迟、回声等效果
 */
class ConvolutionFilter(private val impulseResponse: DoubleArray) {

    init {
        require(impulseResponse.isNotEmpty()) { "冲激响应不能为空" }
    }

    /**
     * 对样本数组进行卷积处理
     * @param samples 输入样本数组，范围[-1.0, 1.0]
     * @return 卷积后的样本数组
     */
    fun apply(samples: DoubleArray): DoubleArray {
        if (samples.isEmpty()) return DoubleArray(0)

        val inputSamples = samples.size

        // 卷积结果长度
        val outputLength = inputSamples + impulseResponse.size - 1
        val result = DoubleArray(outputLength)

        // 进行卷积运算
        for (n in 0 until outputLength) {
            var sum = 0.0
            for (k in 0 until impulseResponse.size) {
                val inputIndex = n - k
                if (inputIndex >= 0 && inputIndex < inputSamples) {
                    sum += impulseResponse[k] * samples[inputIndex]
                }
            }
            result[n] = sum
        }

        return result
    }

    /**
     * 获取脉冲响应信息
     */
    fun getImpulseInfo(): String {
        val maxVal = impulseResponse.maxOrNull() ?: 0.0
        val minVal = impulseResponse.minOrNull() ?: 0.0
        val avgVal = impulseResponse.average()

        return """
            |脉冲响应信息:
            |- 长度: ${impulseResponse.size} 采样点
            |- 最大值: ${"%.4f".format(maxVal)}
            |- 最小值: ${"%.4f".format(minVal)}
            |- 平均值: ${"%.6f".format(avgVal)}
            |- 能量: ${"%.4f".format(impulseResponse.sumOf { it * it })}
        """.trimMargin()
    }

    companion object {
        /**
         * 创建简单的延迟冲激响应
         * @param delayTime 延迟时间(秒)
         * @param decay 衰减系数(0.0-1.0)
         * @param sampleRate 采样率
         * @return 冲激响应数组
         */
        @JvmStatic
        fun createDelayImpulse(delayTime: Double, decay: Double, sampleRate: Int = 44100): DoubleArray {
            val delaySamples = (delayTime * sampleRate).toInt()
            val impulse = DoubleArray(delaySamples + 1) { 0.0 }

            // 初始脉冲
            impulse[0] = 1.0

            // 延迟脉冲
            if (delaySamples < impulse.size) {
                impulse[delaySamples] = decay
            }

            return impulse
        }

        /**
         * 创建简单混响冲激响应（指数衰减）
         * @param reverbTime 混响时间(秒)
         * @param decayFactor 衰减因子
         * @param sampleRate 采样率
         * @return 冲激响应数组
         */
        @JvmStatic
        fun createReverbImpulse(reverbTime: Double, decayFactor: Double = 0.7, sampleRate: Int = 44100): DoubleArray {
            val reverbSamples = (reverbTime * sampleRate).toInt()
            val impulse = DoubleArray(reverbSamples) { 0.0 }

            // 初始脉冲
            impulse[0] = 1.0

            // 指数衰减的回声
            for (i in 1 until reverbSamples) {
                impulse[i] = Math.pow(decayFactor, i.toDouble()) *
                        (0.5 + 0.5 * Math.sin(i.toDouble() / 100.0))
            }

            // 归一化
            val normFactor = 1.0 / impulse.maxOrNull()!!
            return impulse.map { it * normFactor }.toDoubleArray()
        }

        /**
         * 创建低通滤波器冲激响应（使用sinc函数）
         * @param cutoffFreq 截止频率(Hz)
         * @param filterLength 滤波器长度(抽头数)
         * @param sampleRate 采样率
         * @return 冲激响应数组
         */
        @JvmStatic
        fun createLowPassImpulse(cutoffFreq: Double, filterLength: Int = 101, sampleRate: Int = 44100): DoubleArray {
            require(filterLength % 2 == 1) { "滤波器长度必须是奇数" }

            val impulse = DoubleArray(filterLength)
            val fc = cutoffFreq / sampleRate  // 归一化截止频率
            val M = (filterLength - 1) / 2

            // 使用sinc函数生成理想低通滤波器
            for (n in 0 until filterLength) {
                val nMinusM = n - M
                if (nMinusM == 0) {
                    impulse[n] = 2.0 * fc
                } else {
                    impulse[n] = Math.sin(2.0 * Math.PI * fc * nMinusM) / (Math.PI * nMinusM)
                }
            }

            // 应用汉明窗以减少吉布斯现象
            for (n in 0 until filterLength) {
                val window = 0.54 - 0.46 * Math.cos(2.0 * Math.PI * n / (filterLength - 1))
                impulse[n] *= window
            }

            // 归一化
            val sum = impulse.sum()
            return impulse.map { it / sum }.toDoubleArray()
        }

        /**
         * 从两头检测幅值并裁剪过小的部分
         * @param impulse 原始脉冲响应
         * @param threshold 幅值阈值，绝对值低于此值的部分将被裁剪
         * @param keepRatio 保留比率，裁剪后至少保留原始长度的比例(0.0-1.0)
         * @param minSilenceLength 最小静音长度，连续低于阈值的采样点超过此值才认为是静音段
         * @return 裁剪后的脉冲响应
         */
        @JvmStatic
        fun trimImpulseResponse(
            impulse: DoubleArray,
            threshold: Double = 0.001,
            keepRatio: Double = 0.1,
            minSilenceLength: Int = 10
        ): DoubleArray {
            if (impulse.isEmpty()) return impulse

            // 计算绝对阈值
            val absThreshold = Math.abs(threshold)

            // 计算最小保留长度
            val minKeepLength = (impulse.size * keepRatio).toInt().coerceAtLeast(1)

            // 寻找起始点
            var startIndex = 0
            var silenceCount = 0

            for (i in impulse.indices) {
                if (Math.abs(impulse[i]) < absThreshold) {
                    silenceCount++
                    if (silenceCount >= minSilenceLength) {
                        startIndex = i - minSilenceLength + 1
                        break
                    }
                } else {
                    break
                }
            }

            // 确保不会裁剪过多
            startIndex = startIndex.coerceAtMost(impulse.size - minKeepLength)

            // 寻找结束点
            var endIndex = impulse.size - 1
            silenceCount = 0

            for (i in impulse.size - 1 downTo 0) {
                if (Math.abs(impulse[i]) < absThreshold) {
                    silenceCount++
                    if (silenceCount >= minSilenceLength) {
                        endIndex = i + minSilenceLength - 1
                        break
                    }
                } else {
                    break
                }
            }

            // 确保不会裁剪过多
            endIndex = endIndex.coerceAtLeast(minKeepLength - 1)

            // 确保起始点小于结束点
            if (startIndex >= endIndex) {
                // 如果整个信号都低于阈值，返回中间部分
                startIndex = impulse.size / 4
                endIndex = (impulse.size * 3 / 4).coerceAtMost(impulse.size - 1)
            }

            // 返回裁剪后的部分
            return impulse.copyOfRange(startIndex, endIndex + 1)
        }

        /**
         * 从资源文件加载WAV格式的脉冲响应
         * @param resourcePath 资源路径，例如 "/impulses/hall_reverb.wav"
         * @param targetSampleRate 目标采样率（如果需要重采样）
         * @param normalize 是否归一化脉冲响应
         * @param maxLength 最大长度限制（采样点数），0表示无限制
         * @param trimSilence 是否裁剪两头的静音部分
         * @param trimThreshold 裁剪阈值
         * @param keepRatio 裁剪后至少保留的比例
         * @return 脉冲响应数组
         */
        @JvmStatic
        @Throws(Exception::class)
        fun loadImpulseFromWavResource(
            resourcePath: String,
            targetSampleRate: Int = 44100,
            normalize: Boolean = true,
            maxLength: Int = 0,
            trimSilence: Boolean = true,
            trimThreshold: Double = 0.001,
            keepRatio: Double = 0.1
        ): DoubleArray {
            // 获取资源流
            val inputStream = ConvolutionFilter::class.java.getResourceAsStream(resourcePath)
                ?: throw IllegalArgumentException("找不到资源文件: $resourcePath")

            return try {
                loadImpulseFromWavStream(
                    inputStream,
                    targetSampleRate,
                    normalize,
                    maxLength,
                    trimSilence,
                    trimThreshold,
                    keepRatio
                )
            } finally {
                inputStream.close()
            }
        }

        /**
         * 从InputStream加载WAV脉冲响应
         */
        @JvmStatic
        @Throws(Exception::class)
        fun loadImpulseFromWavStream(
            inputStream: InputStream,
            targetSampleRate: Int = 44100,
            normalize: Boolean = true,
            maxLength: Int = 0,
            trimSilence: Boolean = true,
            trimThreshold: Double = 0.001,
            keepRatio: Double = 0.1
        ): DoubleArray {
            // 读取整个流到字节数组（因为AudioInputStream需要支持mark/reset）
            val byteStream = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                byteStream.write(buffer, 0, bytesRead)
            }
            val audioBytes = byteStream.toByteArray()

            // 创建AudioInputStream
            val byteArrayInputStream = java.io.ByteArrayInputStream(audioBytes)
            val audioInputStream = AudioSystem.getAudioInputStream(byteArrayInputStream)

            return try {
                // 获取音频格式
                val format = audioInputStream.format

                // 转换到目标格式（单声道，16位，目标采样率）
                val targetFormat = SoundHelper.defaultAudioFormat

                // 检查是否需要转换
                val convertedStream = if (!format.matches(targetFormat)) {
                    if (AudioSystem.isConversionSupported(targetFormat, format)) {
                        AudioSystem.getAudioInputStream(targetFormat, audioInputStream)
                    } else {
                        throw IllegalStateException("不支持的音频格式转换")
                    }
                } else {
                    audioInputStream
                }

                // 读取音频数据
                val frameSize = targetFormat.frameSize // 每帧字节数
                val frameLength = convertedStream.frameLength
                val totalBytes = frameLength * frameSize

                // 读取所有字节
                val audioData = ByteArray(totalBytes.toInt())
                var bytesReadTotal = 0
                while (bytesReadTotal < audioData.size) {
                    val read = convertedStream.read(audioData, bytesReadTotal, audioData.size - bytesReadTotal)
                    if (read == -1) break
                    bytesReadTotal += read
                }

                // 将16位PCM转换为double数组 [-1.0, 1.0]
                val samples = mutableListOf<Double>()
                for (i in 0 until bytesReadTotal step 2) {
                    if (i + 1 < bytesReadTotal) {
                        // 16位PCM小端序
                        val sample = ((audioData[i].toInt() and 0xFF) or
                                ((audioData[i + 1].toInt() and 0xFF) shl 8)).toShort()
                        // 转换为 [-1.0, 1.0] 范围
                        samples.add(sample.toDouble() / 32768.0)
                    }
                }

                var result = samples.toDoubleArray()

                // 裁剪静音部分
                if (trimSilence && result.isNotEmpty()) {
                    result = trimImpulseResponse(result, trimThreshold, keepRatio)
                    println("裁剪后长度: ${result.size} (原始: ${samples.size})")
                }

                // 应用长度限制
                if (maxLength > 0 && result.size > maxLength) {
                    result = result.copyOfRange(0, maxLength)
                    println("限制长度后: ${result.size}")
                }

                // 归一化（可选）
                if (normalize && result.isNotEmpty()) {
                    val maxVal = result.maxByOrNull { Math.abs(it) } ?: 1.0
                    if (Math.abs(maxVal) > 1e-10) {
                        val scale = 1.0 / maxVal
                        for (i in result.indices) {
                            result[i] *= scale
                        }
                        println("归一化完成，缩放因子: $scale")
                    }
                }

                result

            } finally {
                audioInputStream.close()
            }
        }

        /**
         * 测试主方法 - 用于测试WAV脉冲响应的加载和应用
         */
        @JvmStatic
        fun main(args: Array<String>) {
            println("=== 卷积滤波器测试程序 ===")
            println()

            // 1. 生成基础测试信号（正弦波）
            println("1. 生成测试信号...")
            val testSignal = WaveGenerators.sineWave(
                duration = 1.5,
                frequency = 440.0, // A4音
                amplitude = 0.5,
                sampleRate = 44100
            )
            println("   生成 ${testSignal.size} 个采样点")

            // 2. 播放原始信号
            println("2. 播放原始正弦波信号（440Hz A4）...")
            SoundHelper.playSound(testSignal.toSoundData())

            // 加载脉冲响应
            val impulse = createDelayImpulse(1.0, 0.3)
            println("✓ 加载成功: ${impulse.size} 个采样点")

            // 播放脉冲响应本身（听一下它是什么样的）
            println("播放脉冲响应本身...")
            SoundHelper.playSound(impulse.toSoundData())
            SoundHelper.playSound(impulse.toSoundData())
            Thread.sleep(100)

            // 显示脉冲响应信息
            val filter = ConvolutionFilter(impulse)
            println(filter.getImpulseInfo())

            // 创建卷积滤波器
            println("应用卷积滤波...")
            val startTime = System.currentTimeMillis()

            // 应用卷积
            val processedSignal = filter.apply(testSignal)

            val endTime = System.currentTimeMillis()
            println("卷积处理耗时: ${endTime - startTime}ms")
            println("输出信号长度: ${processedSignal.size} 个采样点")

            // 播放处理后的信号
            println("播放处理后的声音...")
            SoundHelper.playSound(testSignal.toSoundData())
            SoundHelper.playSound(processedSignal.toSoundData())
            SoundHelper.playSound(testSignal.toSoundData())
            SoundHelper.playSound(processedSignal.toSoundData())

            println()
            println("=== 测试完成 ===")
        }
    }
}