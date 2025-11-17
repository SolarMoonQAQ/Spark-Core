package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.sound.SoundData
import java.io.ByteArrayInputStream
import javax.sound.sampled.*

/**
 * 声音播放器工具类
 * 提供静态方法用于播放SoundData
 */
object SoundPlayer {

    /**
     * 播放SoundData
     * @param soundData 要播放的声音数据
     */
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
    fun play(synthesizer: () -> SoundData) {
        playSound(synthesizer())
    }
}

// ============================ 测试运行类 ============================

/**
 * 声音播放器测试运行器
 */
object SimpleSoundPlayerTest {

    /**
     * 主函数 - 用于测试声音播放
     */
    @JvmStatic
    fun main(args: Array<String>) {
        // 方式1：直接使用SoundPlayer播放SoundData
        val soundData = SoundSynthesizers.sineWave(2.0, 440.0, 0.5)
        SoundPlayer.playSound(soundData)

        // 方式2：使用lambda表达式
        SoundPlayer.play {
            SoundSynthesizers.sineWave(2.0, 440.0, 0.5)
        }
    }
}