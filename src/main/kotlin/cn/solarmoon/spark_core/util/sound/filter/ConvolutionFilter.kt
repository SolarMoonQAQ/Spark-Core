package cn.solarmoon.spark_core.util.sound.filter

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
    }
}