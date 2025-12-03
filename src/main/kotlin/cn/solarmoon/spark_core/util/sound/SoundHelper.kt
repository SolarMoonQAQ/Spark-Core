package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.sound.SoundData
import cn.solarmoon.spark_core.util.sound.WaveGenerators
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.sound.sampled.*

/**
 * 声音播放器工具类
 * 提供静态方法用于播放SoundData
 */
object SoundHelper {

    val defaultAudioFormat = AudioFormat(44100.0f, 16, 1, true, false)

    /**
     * 播放SoundData
     * @param soundData 要播放的声音数据
     */
    @JvmStatic
    fun playSound(soundData: SoundData) {
        try {
            val buffer = soundData.byteBuffer()
            val format = soundData.audioFormat()

            val audioFormat = AudioFormat(
                format.sampleRate,
                format.sampleSizeInBits,
                format.channels,
                format.encoding == AudioFormat.Encoding.PCM_SIGNED,
                format.isBigEndian
            )

            val data = ByteArray(buffer.remaining())
            buffer.rewind()
            buffer.get(data)

            val clip = AudioSystem.getClip()
            val audioInputStream = AudioInputStream(
                ByteArrayInputStream(data),
                audioFormat,
                data.size.toLong() / (format.sampleSizeInBits / 8) / format.channels
            )

            clip.open(audioInputStream)
            clip.start()

            // 等待播放完成
            Thread.sleep((soundData.byteBuffer().capacity() / (format.sampleSizeInBits / 8) / format.channels / format.sampleRate * 1000).toLong() + 100)
            clip.close()

        } catch (e: Exception) {
            println("播放失败: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * 便捷方法：直接播放合成的声音数据
     * @param synthesizer 产生声音数据的lambda函数
     */
    @JvmStatic
    fun play(synthesizer: () -> SoundData) {
        playSound(synthesizer())
    }

    /**
     * 主函数 - 用于测试声音播放
     */
    @JvmStatic
    fun main(args: Array<String>) {
        // 方式1：直接使用SoundPlayer播放SoundData
        val soundData = WaveGenerators.sineWave(2.0, 440.0, 0.5).toSoundData()
        playSound(soundData)

        // 方式2：使用lambda表达式
        play {
            WaveGenerators.sineWave(2.0, 440.0, 0.5).toSoundData()
        }
    }

}

/**
 * 扩展方法：将DoubleArray转换为SoundData
 * @return SoundData对象
 */
fun DoubleArray.toSoundData(): SoundData {
    val byteBuffer = ByteBuffer.allocateDirect(this.size * 2).order(ByteOrder.LITTLE_ENDIAN)

    for (sample in this) {
        val sampleValue = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt()
        byteBuffer.putShort(sampleValue.toShort())
    }

    byteBuffer.rewind()
    return SoundData(byteBuffer, SoundHelper.defaultAudioFormat)
}

/**
 * 扩展方法：将SoundData转换为DoubleArray
 * @return DoubleArray样本数组，范围[-1.0, 1.0]
 */
fun SoundData.toDoubleArray(): DoubleArray {
    val buffer = this.byteBuffer()
    val totalSamples = buffer.capacity() / 2
    val result = DoubleArray(totalSamples)

    for (i in 0 until totalSamples) {
        result[i] = buffer.getShort(i * 2).toDouble() / Short.MAX_VALUE.toDouble()
    }

    return result
}