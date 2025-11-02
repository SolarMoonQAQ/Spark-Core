package cn.solarmoon.spark_core.util.visualization

import cn.solarmoon.spark_core.util.SoundSynthesizers
import org.lwjgl.opengl.GL11.*
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.math.*

/**
 * 音频可视化工具
 */
class SoundVisualizer {

    companion object {
        private const val WINDOW_WIDTH = 1200
        private const val WINDOW_HEIGHT = 800
        private const val MARGIN = 50

        /**
         * 保存波形图为PNG
         */
        @JvmStatic
        fun saveWaveformImage(soundData: cn.solarmoon.spark_core.sound.SoundData, filename: String) {
            val buffer = soundData.byteBuffer()
            val totalSamples = buffer.capacity() / 2
            val samples = FloatArray(totalSamples)

            for (i in 0 until totalSamples) {
                samples[i] = buffer.getShort(i * 2).toFloat() / Short.MAX_VALUE.toFloat()
            }

            val image = BufferedImage(800, 600, BufferedImage.TYPE_INT_RGB)
            val g = image.createGraphics()

            // 填充背景
            g.color = Color.BLACK
            g.fillRect(0, 0, 800, 600)

            // 绘制网格
            g.color = Color(50, 50, 50)
            for (i in 0..10) {
                val x = (800 * i / 10)
                val y = (600 * i / 10)
                g.drawLine(x, 0, x, 600)
                g.drawLine(0, y, 800, y)
            }

            // 绘制波形
            g.color = Color.GREEN
            val centerY = 300
            val scaleY = 250.0f

            for (i in 0 until samples.size - 1) {
                val x1 = (800 * i / samples.size.toFloat()).toInt()
                val x2 = (800 * (i + 1) / samples.size.toFloat()).toInt()
                val y1 = centerY - (samples[i] * scaleY).toInt()
                val y2 = centerY - (samples[i + 1] * scaleY).toInt()
                g.drawLine(x1, y1, x2, y2)
            }

            // 添加标题
            g.color = Color.WHITE
            g.drawString("Waveform: ${soundData.audioFormat().sampleRate}Hz, ${totalSamples} samples", 10, 20)

            g.dispose()

            // 保存图片
            ImageIO.write(image, "PNG", File("$filename.png"))
            println("波形图已保存: $filename.png")
        }

        /**
         * 计算频谱数据
         */
        private fun calculateSpectrum(samples: FloatArray, sampleRate: Int): FloatArray {
            // 简单的FFT实现（实际项目中建议使用成熟库如JTransforms）
            val n = samples.size
            val spectrum = FloatArray(n / 2)

            // 应用汉宁窗
            val windowed = FloatArray(n)
            for (i in 0 until n) {
                val window = 0.5f * (1 - cos(2 * PI * i / (n - 1)).toFloat())
                windowed[i] = samples[i] * window
            }

            // 计算幅度谱（简化版本）
            for (freqBin in 0 until n / 2) {
                var real = 0.0f
                var imag = 0.0f

                for (t in 0 until n) {
                    val angle = 2 * PI * freqBin * t / n
                    real += windowed[t] * cos(angle).toFloat()
                    imag -= windowed[t] * sin(angle).toFloat()
                }

                val magnitude = sqrt(real * real + imag * imag) / n
                spectrum[freqBin] = magnitude
            }

            return spectrum
        }

        /**
         * 绘制时域波形
         */
        private fun drawTimeDomain(samples: FloatArray, title: String) {
            val graphWidth = WINDOW_WIDTH - 2 * MARGIN
            val graphHeight = (WINDOW_HEIGHT / 2) - 2 * MARGIN
            val startY = WINDOW_HEIGHT - MARGIN - graphHeight

            // 绘制背景
            glColor4f(0.2f, 0.2f, 0.2f, 1.0f)
            glBegin(GL_QUADS)
            glVertex2f(MARGIN.toFloat(), startY.toFloat())
            glVertex2f((MARGIN + graphWidth).toFloat(), startY.toFloat())
            glVertex2f((MARGIN + graphWidth).toFloat(), (startY + graphHeight).toFloat())
            glVertex2f(MARGIN.toFloat(), (startY + graphHeight).toFloat())
            glEnd()

            // 绘制网格
            glColor4f(0.3f, 0.3f, 0.3f, 1.0f)
            glBegin(GL_LINES)
            // 水平线
            for (i in 0..4) {
                val y = startY + (graphHeight * i / 4)
                glVertex2f(MARGIN.toFloat(), y.toFloat())
                glVertex2f((MARGIN + graphWidth).toFloat(), y.toFloat())
            }
            // 垂直线
            for (i in 0..8) {
                val x = MARGIN + (graphWidth * i / 8)
                glVertex2f(x.toFloat(), startY.toFloat())
                glVertex2f(x.toFloat(), (startY + graphHeight).toFloat())
            }
            glEnd()

            // 绘制波形
            glColor4f(0.0f, 1.0f, 0.0f, 1.0f)
            glBegin(GL_LINE_STRIP)

            val pointsToDraw = min(samples.size, 2000) // 限制点数以提高性能
            for (i in 0 until pointsToDraw) {
                val x = MARGIN + (graphWidth * i / pointsToDraw.toFloat())
                val y = startY + graphHeight / 2 + (samples[i] * graphHeight / 2)
                glVertex2f(x.toFloat(), y.toFloat())
            }
            glEnd()

            // 绘制标题和坐标轴标签
            drawText(MARGIN + 10, startY + graphHeight - 20, title, 1.0f, 1.0f, 1.0f)
            drawText(MARGIN + 10, startY + 20, "Amplitude", 0.8f, 0.8f, 0.8f)
        }

        /**
         * 绘制频域频谱
         */
        private fun drawFrequencyDomain(spectrum: FloatArray, title: String) {
            val graphWidth = WINDOW_WIDTH - 2 * MARGIN
            val graphHeight = (WINDOW_HEIGHT / 2) - 2 * MARGIN
            val startY = MARGIN

            // 绘制背景
            glColor4f(0.2f, 0.2f, 0.2f, 1.0f)
            glBegin(GL_QUADS)
            glVertex2f(MARGIN.toFloat(), startY.toFloat())
            glVertex2f((MARGIN + graphWidth).toFloat(), startY.toFloat())
            glVertex2f((MARGIN + graphWidth).toFloat(), (startY + graphHeight).toFloat())
            glVertex2f(MARGIN.toFloat(), (startY + graphHeight).toFloat())
            glEnd()

            // 绘制频谱
            glColor4f(1.0f, 0.5f, 0.0f, 1.0f)
            glBegin(GL_LINE_STRIP)

            val pointsToDraw = min(spectrum.size, 1000)
            for (i in 0 until pointsToDraw) {
                val x = MARGIN + (graphWidth * i / pointsToDraw.toFloat())
                val magnitude = min(spectrum[i] * 100, 1.0f) // 缩放幅度
                val y = startY + (magnitude * graphHeight)
                glVertex2f(x.toFloat(), y.toFloat())
            }
            glEnd()

            // 绘制标题
            drawText(MARGIN + 10, startY + graphHeight - 20, title, 1.0f, 1.0f, 1.0f)
            drawText(MARGIN + 10, startY + 20, "Magnitude", 0.8f, 0.8f, 0.8f)
        }

        /**
         * 绘制信息面板
         */
        private fun drawInfoPanel(soundData: cn.solarmoon.spark_core.sound.SoundData, samples: FloatArray, spectrum: FloatArray) {
            val format = soundData.audioFormat()
            val infoX = WINDOW_WIDTH - 250
            var infoY = WINDOW_HEIGHT - 150

            glColor4f(0.1f, 0.1f, 0.1f, 0.8f)
            glBegin(GL_QUADS)
            glVertex2f((infoX - 10).toFloat(), (infoY - 10).toFloat())
            glVertex2f((WINDOW_WIDTH - 10).toFloat(), (infoY - 10).toFloat())
            glVertex2f((WINDOW_WIDTH - 10).toFloat(), (infoY + 140).toFloat())
            glVertex2f((infoX - 10).toFloat(), (infoY + 140).toFloat())
            glEnd()

            glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
            drawText(infoX, infoY, "Audio Info:", 1.0f, 1.0f, 1.0f)
            infoY += 20
            drawText(infoX, infoY, "Sample Rate: ${format.sampleRate} Hz", 0.8f, 0.8f, 0.8f)
            infoY += 15
            drawText(infoX, infoY, "Bits: ${format.sampleSizeInBits}", 0.8f, 0.8f, 0.8f)
            infoY += 15
            drawText(infoX, infoY, "Channels: ${format.channels}", 0.8f, 0.8f, 0.8f)
            infoY += 15

            // 计算统计信息
            val maxAmplitude = samples.maxOrNull() ?: 0f
            val minAmplitude = samples.minOrNull() ?: 0f
            val rms = sqrt(samples.map { it * it }.average()).toFloat()

            drawText(infoX, infoY, "Max: ${"%.3f".format(maxAmplitude)}", 0.8f, 0.8f, 0.8f)
            infoY += 15
            drawText(infoX, infoY, "Min: ${"%.3f".format(minAmplitude)}", 0.8f, 0.8f, 0.8f)
            infoY += 15
            drawText(infoX, infoY, "RMS: ${"%.3f".format(rms)}", 0.8f, 0.8f, 0.8f)
            infoY += 15

            // 找到频谱峰值
            val maxFreqIndex = spectrum.indices.maxByOrNull { spectrum[it] } ?: 0
            val peakFreq = maxFreqIndex * format.sampleRate.toInt() / spectrum.size
            drawText(infoX, infoY, "Peak Freq: ${peakFreq} Hz", 0.8f, 0.8f, 0.8f)
        }

        /**
         * 简单的文本绘制（使用GL点阵图）
         */
        private fun drawText(x: Int, y: Int, text: String, r: Float, g: Float, b: Float) {
            glColor3f(r, g, b)
            glRasterPos2f(x.toFloat(), y.toFloat())

            // 注意：这是一个简化版本，实际应该使用字体纹理
            // 这里只是绘制点来表示文本位置
            glBegin(GL_POINTS)
            for (i in text.indices) {
                glVertex2f((x + i * 6).toFloat(), y.toFloat())
            }
            glEnd()
        }
    }
}

/**
 * 声音可视化测试
 */
object SoundVisualizationTest {

    @JvmStatic
    fun main(args: Array<String>) {
        println("开始声音合成和可视化测试...")

        // 测试不同的波形
        testSineWave()
        testSquareWave()
        testWhiteNoise()
        testComplexSound()

        println("测试完成！")
    }

    private fun testSineWave() {
        println("测试正弦波...")
        val sineWave = SoundSynthesizers.sineWave(
            duration = 1.0f,
            frequency = 1.0f, // A4
            amplitude = 0.8f
        )

        // 保存为图片
        SoundVisualizer.saveWaveformImage(sineWave, "sine_wave_440hz")

    }

    private fun testSquareWave() {
        println("测试方波...")
        val squareWave = SoundSynthesizers.squareWave(
            duration = 1.0f,
            frequency = 1.0f,
            amplitude = 0.6f,
            dutyCycle = 0.5f
        )

        SoundVisualizer.saveWaveformImage(squareWave, "square_wave_220hz")
    }

    private fun testWhiteNoise() {
        println("测试白噪音...")
        val whiteNoise = SoundSynthesizers.whiteNoise(
            duration = 1.0f,
            amplitude = 0.5f,
            true
        )

        SoundVisualizer.saveWaveformImage(whiteNoise, "white_noise")
        // 应用滤波器测试
        val filtered = SoundSynthesizers.highPassFilter(whiteNoise, 800f)
        SoundVisualizer.saveWaveformImage(filtered, "filtered_white_noise")
    }

    private fun testComplexSound() {
        println("测试复杂声音...")
        val motorSound = SoundSynthesizers.motorSound(
            duration = 3.0f,
            baseFrequency = 10.0f,
            roughness = 0.2f
        )

        SoundVisualizer.saveWaveformImage(motorSound, "motor_sound")

        // 应用滤波器测试
        val filtered = SoundSynthesizers.lowPassFilter(motorSound, 800f)
        SoundVisualizer.saveWaveformImage(filtered, "filtered_motor_sound")
    }
}