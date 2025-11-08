// SoundTestGUI.kt
package cn.solarmoon.spark_core.test

import cn.solarmoon.spark_core.sound.SoundData
import cn.solarmoon.spark_core.util.SoundSynthesizers
import cn.solarmoon.spark_core.util.BrushlessMotorSynthesizer
import java.awt.*
import java.awt.event.*
import java.io.ByteArrayInputStream
import java.io.File
import javax.sound.sampled.*
import javax.swing.*

/**
 * 音频合成测试界面
 */
object SoundTestGUI {

    private var currentSoundData: SoundData? = null
    private var currentClip: Clip? = null

    @JvmStatic
    fun main(args: Array<String>) {
        SwingUtilities.invokeLater { createAndShowGUI() }
    }

    private fun createAndShowGUI() {
        val frame = JFrame("音频合成测试工具")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.layout = BorderLayout()

        // 创建选项卡面板
        val tabbedPane = JTabbedPane()

        // 基础波形选项卡
        tabbedPane.addTab("基础波形", createBasicWavePanel())

        // 无刷电机音效选项卡
        tabbedPane.addTab("无刷电机音效", createBrushlessMotorPanel())

        // 滤波器选项卡
        tabbedPane.addTab("滤波器", createFilterPanel())

        frame.add(tabbedPane, BorderLayout.CENTER)

        // 添加控制按钮面板
        frame.add(createControlPanel(), BorderLayout.SOUTH)

        frame.setSize(800, 600)
        frame.setLocationRelativeTo(null)
        frame.isVisible = true
    }

    /**
     * 基础波形面板
     */
    private fun createBasicWavePanel(): JPanel {
        val panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints()
        gbc.insets = Insets(5, 5, 5, 5)
        gbc.fill = GridBagConstraints.HORIZONTAL

        // 波形选择
        gbc.gridx = 0
        gbc.gridy = 0
        panel.add(JLabel("波形类型:"), gbc)

        gbc.gridx = 1
        val waveCombo = JComboBox(arrayOf("正弦波", "方波", "锯齿波", "三角波", "脉冲波", "白噪声"))
        panel.add(waveCombo, gbc)

        // 频率调节
        gbc.gridx = 0
        gbc.gridy = 1
        panel.add(JLabel("频率 (Hz):"), gbc)

        gbc.gridx = 1
        val freqSlider = JSlider(50, 2000, 440)
        freqSlider.majorTickSpacing = 500
        freqSlider.minorTickSpacing = 100
        freqSlider.paintTicks = true
        freqSlider.paintLabels = true
        panel.add(freqSlider, gbc)

        // 振幅调节
        gbc.gridx = 0
        gbc.gridy = 2
        panel.add(JLabel("振幅:"), gbc)

        gbc.gridx = 1
        val ampSlider = JSlider(0, 100, 50)
        ampSlider.paintTicks = true
        ampSlider.paintLabels = true
        panel.add(ampSlider, gbc)

        // 持续时间
        gbc.gridx = 0
        gbc.gridy = 3
        panel.add(JLabel("持续时间 (秒):"), gbc)

        gbc.gridx = 1
        val durationSpinner = JSpinner(SpinnerNumberModel(2.0, 0.1, 10.0, 0.1))
        panel.add(durationSpinner, gbc)

        // 特殊参数面板（动态显示）
        val specialParamsPanel = JPanel()
        specialParamsPanel.layout = FlowLayout()

        // 监听波形选择变化
        waveCombo.addActionListener {
            updateSpecialParams(specialParamsPanel, waveCombo.selectedItem as String)
        }

        gbc.gridx = 0
        gbc.gridy = 4
        gbc.gridwidth = 2
        panel.add(specialParamsPanel, gbc)

        // 初始更新
        updateSpecialParams(specialParamsPanel, waveCombo.selectedItem as String)

        // 测试按钮
        gbc.gridy = 5
        val testButton = JButton("生成并播放")
        testButton.addActionListener {
            generateAndPlayBasicWave(
                waveCombo.selectedItem as String,
                freqSlider.value.toDouble(),
                ampSlider.value / 100.0,
                durationSpinner.value as Double,
                specialParamsPanel
            )
        }
        panel.add(testButton, gbc)

        return panel
    }

    /**
     * 更新特殊参数面板
     */
    private fun updateSpecialParams(panel: JPanel, waveType: String) {
        panel.removeAll()

        when (waveType) {
            "方波" -> {
                panel.add(JLabel("占空比:"))
                val dutyCycleSlider = JSlider(0, 100, 50)
                dutyCycleSlider.name = "dutyCycle"
                dutyCycleSlider.paintTicks = true
                panel.add(dutyCycleSlider)
            }
            "锯齿波" -> {
                val risingCheck = JCheckBox("上升锯齿", true)
                risingCheck.name = "rising"
                panel.add(risingCheck)
            }
            "三角波" -> {
                panel.add(JLabel("对称性:"))
                val symmetrySlider = JSlider(0, 100, 50)
                symmetrySlider.name = "symmetry"
                symmetrySlider.paintTicks = true
                panel.add(symmetrySlider)
            }
            "脉冲波" -> {
                panel.add(JLabel("脉冲宽度:"))
                val pulseWidthSlider = JSlider(1, 50, 10)
                pulseWidthSlider.name = "pulseWidth"
                pulseWidthSlider.paintTicks = true
                panel.add(pulseWidthSlider)
            }
            "白噪声" -> {
                val gaussianCheck = JCheckBox("高斯分布", true)
                gaussianCheck.name = "gaussian"
                panel.add(gaussianCheck)
            }
        }

        panel.revalidate()
        panel.repaint()
    }

    /**
     * 生成并播放基础波形
     */
    private fun generateAndPlayBasicWave(
        waveType: String,
        frequency: Double,
        amplitude: Double,
        duration: Double,
        paramsPanel: JPanel
    ) {
        val soundData = when (waveType) {
            "正弦波" -> SoundSynthesizers.sineWave(duration, frequency, amplitude)
            "方波" -> {
                val dutyCycle = findComponent<JSlider>(paramsPanel, "dutyCycle")?.value?.toDouble()?.div(100) ?: 0.5
                SoundSynthesizers.squareWave(duration, frequency, amplitude, dutyCycle)
            }
            "锯齿波" -> {
                val rising = findComponent<JCheckBox>(paramsPanel, "rising")?.isSelected ?: true
                SoundSynthesizers.sawtoothWave(duration, frequency, amplitude, rising = rising)
            }
            "三角波" -> {
                val symmetry = findComponent<JSlider>(paramsPanel, "symmetry")?.value?.toDouble()?.div(100) ?: 0.5
                SoundSynthesizers.triangleWave(duration, frequency, amplitude, symmetry = symmetry)
            }
            "脉冲波" -> {
                val pulseWidth = findComponent<JSlider>(paramsPanel, "pulseWidth")?.value?.toDouble()?.div(100) ?: 0.1
                SoundSynthesizers.pulseWave(duration, frequency, amplitude, pulseWidth)
            }
            "白噪声" -> {
                val gaussian = findComponent<JCheckBox>(paramsPanel, "gaussian")?.isSelected ?: true
                SoundSynthesizers.whiteNoise(duration, amplitude, gaussian)
            }
            else -> SoundSynthesizers.sineWave(duration, frequency, amplitude)
        }

        currentSoundData = soundData
        playSound(soundData)
    }

    /**
     * 无刷电机音效面板
     */
    private fun createBrushlessMotorPanel(): JPanel {
        val panel = JPanel(GridLayout(0, 2, 10, 10))
        panel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        // 转速调节
        panel.add(JLabel("转速 (RPM):"))
        val rpmSlider = JSlider(500, 20000, 5000)
        rpmSlider.majorTickSpacing = 5000
        rpmSlider.minorTickSpacing = 1000
        rpmSlider.paintTicks = true
        rpmSlider.paintLabels = true
        panel.add(rpmSlider)

        // 负载调节
        panel.add(JLabel("负载 (0.0-1.0):"))
        val loadSlider = JSlider(0, 100, 30)
        loadSlider.paintTicks = true
        loadSlider.paintLabels = true
        panel.add(loadSlider)

        // 油门调节
        panel.add(JLabel("油门 (0.0-1.0):"))
        val throttleSlider = JSlider(0, 100, 50)
        throttleSlider.paintTicks = true
        throttleSlider.paintLabels = true
        panel.add(throttleSlider)

        // 持续时间
        panel.add(JLabel("持续时间 (秒):"))
        val durationSpinner = JSpinner(SpinnerNumberModel(3.0, 1.0, 10.0, 0.5))
        panel.add(durationSpinner)

        // 电机预设选择
        panel.add(JLabel("电机预设:"))
        val presetCombo = JComboBox(arrayOf("默认", "无人机电机", "电动汽车电机", "工业电机"))
        panel.add(presetCombo)

        // 极对数设置
        panel.add(JLabel("极对数:"))
        val polePairsSpinner = JSpinner(SpinnerNumberModel(4, 1, 12, 1))
        panel.add(polePairsSpinner)

        // 测试按钮
        val testButton = JButton("生成无刷电机音效")
        testButton.addActionListener {
            val rpm = rpmSlider.value.toDouble()
            val load = loadSlider.value / 100.0
            val throttle = throttleSlider.value / 100.0
            val duration = durationSpinner.value as Double
            val polePairs = polePairsSpinner.value as Int

            val config = when (presetCombo.selectedItem as String) {
                "无人机电机" -> BrushlessMotorSynthesizer.MotorPresets.droneMotor()
                "电动汽车电机" -> BrushlessMotorSynthesizer.MotorPresets.evMotor()
                "工业电机" -> BrushlessMotorSynthesizer.MotorPresets.industrialMotor()
                else -> BrushlessMotorSynthesizer.MotorConfig(polePairs = polePairs)
            }

            val state = BrushlessMotorSynthesizer.MotorState(
                rpm = rpm,
                load = load,
                throttle = throttle
            )

            val soundData = BrushlessMotorSynthesizer.synthesizeBrushlessMotor(
                duration = duration,
                state = state,
                config = config
            )

            currentSoundData = soundData
            playSound(soundData)
        }

        panel.add(JLabel()) // 空标签占位
        panel.add(testButton)

        return panel
    }

    /**
     * 滤波器面板
     */
    private fun createFilterPanel(): JPanel {
        val panel = JPanel(BorderLayout())

        // 滤波器选择
        val filterPanel = JPanel(FlowLayout())
        filterPanel.add(JLabel("滤波器类型:"))
        val filterCombo = JComboBox(arrayOf("无", "低通", "高通", "带通"))
        filterPanel.add(filterCombo)

        // 参数面板
        val paramsPanel = JPanel(GridLayout(0, 2, 5, 5))

        // 截止频率
        paramsPanel.add(JLabel("截止频率 (Hz):"))
        val cutoffSlider = JSlider(100, 5000, 1000)
        cutoffSlider.majorTickSpacing = 1000
        cutoffSlider.paintTicks = true
        cutoffSlider.paintLabels = true
        paramsPanel.add(cutoffSlider)

        // 共振/带宽
        paramsPanel.add(JLabel("共振:"))
        val resonanceSlider = JSlider(0, 100, 30)
        resonanceSlider.paintTicks = true
        paramsPanel.add(resonanceSlider)

        panel.add(filterPanel, BorderLayout.NORTH)
        panel.add(paramsPanel, BorderLayout.CENTER)

        // 应用滤波器按钮
        val applyButton = JButton("应用滤波器并播放")
        applyButton.addActionListener {
            val filterType = filterCombo.selectedItem as String
            val cutoff = cutoffSlider.value.toDouble()
            val resonance = resonanceSlider.value / 100.0

            val originalSound = currentSoundData ?: SoundSynthesizers.sineWave(2.0, 440.0, 0.5)
            val filteredSound = when (filterType) {
                "低通" -> SoundSynthesizers.lowPassFilter(originalSound, cutoff, resonance)
                "高通" -> SoundSynthesizers.highPassFilter(originalSound, cutoff)
                "带通" -> SoundSynthesizers.bandPassFilter(originalSound, cutoff, resonance * 500)
                else -> originalSound
            }

            playSound(filteredSound)
        }

        panel.add(applyButton, BorderLayout.SOUTH)

        return panel
    }

    /**
     * 控制面板
     */
    private fun createControlPanel(): JPanel {
        val panel = JPanel(FlowLayout())

        val playButton = JButton("播放")
        playButton.addActionListener {
            currentSoundData?.let { playSound(it) }
        }

        val stopButton = JButton("停止")
        stopButton.addActionListener {
            stopSound()
        }

        val saveButton = JButton("保存为WAV")
        saveButton.addActionListener {
            saveSoundAsWav()
        }

        panel.add(playButton)
        panel.add(stopButton)
        panel.add(saveButton)

        return panel
    }

    /**
     * 播放声音
     */
    private fun playSound(soundData: SoundData) {
        stopSound()

        try {
            val buffer = soundData.byteBuffer()
            val format = soundData.audioFormat()

            // 直接使用原始格式，避免转换错误
            val audioFormat = AudioFormat(
                format.sampleRate,
                format.sampleSizeInBits,
                format.channels,
                format.encoding == AudioFormat.Encoding.PCM_SIGNED,
                format.isBigEndian
            )

            val data = ByteArray(buffer.remaining())
            buffer.rewind() // 确保buffer位置正确
            buffer.get(data)

            val clip = AudioSystem.getClip()
            val audioInputStream = AudioInputStream(
                ByteArrayInputStream(data),
                audioFormat,
                data.size.toLong() / (format.sampleSizeInBits / 8) / format.channels
            )

            clip.open(audioInputStream)
            clip.start()

            currentClip = clip

        } catch (e: Exception) {
            JOptionPane.showMessageDialog(null, "播放失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            e.printStackTrace()
        }
    }

    /**
     * 停止播放
     */
    private fun stopSound() {
        currentClip?.let {
            if (it.isRunning) {
                it.stop()
            }
            it.close()
            currentClip = null
        }
    }

    /**
     * 保存为WAV文件
     */
    private fun saveSoundAsWav() {
        val soundData = currentSoundData ?: run {
            JOptionPane.showMessageDialog(null, "没有可保存的声音数据", "警告", JOptionPane.WARNING_MESSAGE)
            return
        }

        val fileChooser = JFileChooser()
        fileChooser.selectedFile = File("sound_${System.currentTimeMillis()}.wav")

        if (fileChooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            try {
                val file = fileChooser.selectedFile
                // 这里需要添加WAV文件保存逻辑

                JOptionPane.showMessageDialog(null, "保存功能待实现", "信息", JOptionPane.INFORMATION_MESSAGE)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(null, "保存失败: ${e.message}", "错误", JOptionPane.ERROR_MESSAGE)
            }
        }
    }

    /**
     * 在容器中查找指定名称的组件
     */
    private inline fun <reified T : Component> findComponent(container: Container, name: String): T? {
        return findComponentRecursive(container, name, T::class.java)
    }

    /**
     * 递归查找组件的辅助函数
     */
    private fun <T : Component> findComponentRecursive(container: Container, name: String, clazz: Class<T>): T? {
        for (component in container.components) {
            if (component.name == name && clazz.isInstance(component)) {
                @Suppress("UNCHECKED_CAST")
                return component as T
            }
            if (component is Container) {
                findComponentRecursive(component, name, clazz)?.let { return it }
            }
        }
        return null
    }
}