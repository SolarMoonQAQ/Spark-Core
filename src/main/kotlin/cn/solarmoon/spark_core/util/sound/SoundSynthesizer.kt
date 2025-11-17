package cn.solarmoon.spark_core.util

import cn.solarmoon.spark_core.sound.SoundData
import java.io.ByteArrayInputStream
import javax.sound.sampled.*

/**
 * 抽象声音合成器基类
 * 用户可以通过继承此类来创建自定义的声音合成器
 */
abstract class AbstractSoundSynthesizer {

    /**
     * 合成声音数据的主方法，子类必须实现此方法
     * @return 合成的声音数据
     */
    abstract fun synthesize(): SoundData

    /**
     * 播放合成的声音
     * @param loop 是否循环播放，默认false
     * @param loopCount 循环次数，当loop=true时有效，Clip.LOOP_CONTINUOUSLY表示无限循环
     */
    fun playSound() {
        val soundData = synthesize()
        playSoundData(soundData)
    }

    /**
     * 播放SoundData
     */
    private fun playSoundData(soundData: SoundData) {
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

            // 等待播放完成（非循环情况下）
            Thread.sleep((soundData.byteBuffer().capacity() / (format.sampleSizeInBits / 8) / format.channels / format.sampleRate * 1000).toLong() + 100)
            clip.close()

        } catch (e: Exception) {
            println("播放失败: ${e.message}")
            e.printStackTrace()
        }
    }

}

// ============================ 测试运行类 ============================
class MyCustomSynthesizer : AbstractSoundSynthesizer() {
    override fun synthesize(): SoundData {
        // 在这里编写你的声音合成逻辑
        return SoundSynthesizers.sineWave(2.0, 440.0, 0.5)
    }
}
/**
 * 声音合成器测试运行器
 */
object Test {

    /**
     * 主函数 - 用于测试示例合成器
     */
    @JvmStatic
    fun main(args: Array<String>) {
        val mySynthesizer = MyCustomSynthesizer()
        mySynthesizer.playSound() // 播放一次
    }
}