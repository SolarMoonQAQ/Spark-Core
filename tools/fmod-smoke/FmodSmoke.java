import cn.solarmoon.spark_core.pack.NativeLoader;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.lwjgl.fmod.FMOD.*;
import static org.lwjgl.fmod.FMODStudio.*;
import static org.lwjgl.system.MemoryUtil.memAddress;

/** 通过 Spark-Core 的原生库加载入口验证 FMOD 版本与播放链路。 */
public final class FmodSmoke {
    /**
     * 从资源目录或 JAR 加载 FMOD，播放测试音并按需验证混音文件。
     *
     * @param args 首项为资源目录或 Spark-Core JAR，第二项可指定混音 WAV 的输出路径
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: FmodSmoke <native-root-or-mod-jar> [output.wav]");
        }
        Path source = Path.of(args[0]);
        // 目录和 JAR 都交给同一个加载入口，JAR 路径由 ZIP 文件系统提供。
        boolean available;
        if (Files.isRegularFile(source)) {
            try (var jar = FileSystems.newFileSystem(source)) {
                available = NativeLoader.loadFmod(jar.getPath("/" + NativeLoader.FMOD_RESOURCE_ROOT));
            }
        } else {
            available = NativeLoader.loadFmod(source);
        }
        // 探针专用于验证播放。加载器的静音结果在这里应作为测试失败报告。
        if (!available) {
            throw new IllegalStateException("FMOD 音效不可用，加载原因见日志");
        }
        Path output = args.length == 2 ? Path.of(args[1]).toAbsolutePath() : null;
        if (output != null) {
            Files.createDirectories(output.getParent());
        }
        Path tone = Files.createTempFile("fmod-smoke-tone-", ".wav");
        try {
            writeTone(tone);
            playTone(tone, output);
            if (output != null) {
                checkRenderedTone(output);
            }
            System.out.println("PASS: FMOD playback advanced through the test tone.");
        } finally {
            Files.deleteIfExists(tone);
        }
    }

    /** 生成带首尾淡入淡出的单声道 440 Hz 测试音。 */
    private static void writeTone(Path file) throws Exception {
        int rate = 48_000;
        int frames = rate * 3 / 2;
        int fadeFrames = rate / 50;
        ByteBuffer pcm = ByteBuffer.allocate(frames * Short.BYTES).order(ByteOrder.LITTLE_ENDIAN);
        for (int frame = 0; frame < frames; frame++) {
            double gain = Math.min(1.0, Math.min(frame, frames - 1 - frame) / (double) fadeFrames);
            pcm.putShort((short) (8192 * gain * Math.sin(2 * Math.PI * 440 * frame / rate)));
        }
        AudioFormat format = new AudioFormat(rate, 16, 1, true, false);
        try (var audio = new AudioInputStream(new ByteArrayInputStream(pcm.array()), format, frames)) {
            AudioSystem.write(audio, AudioFileFormat.Type.WAVE, file.toFile());
        }
    }

    /** 初始化 Studio，播放测试音，并检查 Core 通道的播放位置确实向前推进。 */
    private static void playTone(Path tone, Path output) throws Exception {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointer = stack.mallocPointer(1);
            IntBuffer value = stack.mallocInt(1);
            check("Studio create", FMOD_Studio_System_Create(pointer, FMOD_VERSION));
            long studio = pointer.get(0);
            try {
                check("Get Core system", FMOD_Studio_System_GetCoreSystem(studio, pointer));
                long core = pointer.get(0);
                IntBuffer build = stack.mallocInt(1);
                check("Get FMOD version", FMOD_System_GetVersion(core, value, build));
                int version = value.get(0);
                System.out.printf("FMOD runtime=0x%08x (build %d), LWJGL headers=0x%08x%n",
                        version, build.get(0), FMOD_VERSION);
                // 绑定使用 2.03 的头文件，运行库可以使用该系列的后续补丁版本。
                if (version != NativeLoader.FMOD_RUNTIME_VERSION) {
                    throw new IllegalStateException("FMOD 运行库版本与 Spark-Core 的随包版本不符");
                }

                // 指定 WAV 时由 FMOD 直接写出混音，其余情况使用系统默认播放设备。
                long outputPath = 0;
                if (output != null) {
                    check("Select WAV output", FMOD_System_SetOutput(core, FMOD_OUTPUTTYPE_WAVWRITER));
                    outputPath = memAddress(stack.UTF8(output.toString()));
                }
                check("Studio initialize", FMOD_Studio_System_Initialize(
                        studio, 32, FMOD_STUDIO_INIT_NORMAL, FMOD_INIT_NORMAL, outputPath));
                check("Get output type", FMOD_System_GetOutput(core, value));
                int outputType = value.get(0);
                if (output == null) {
                    check("Get playback devices", FMOD_System_GetNumDrivers(core, value));
                    if (value.get(0) == 0 || outputType == FMOD_OUTPUTTYPE_NOSOUND
                            || outputType == FMOD_OUTPUTTYPE_NOSOUND_NRT) {
                        throw new IllegalStateException("FMOD opened a silent output. Use a playback device or pass output.wav.");
                    }
                    System.out.printf("Output type=%d, playback devices=%d%n", outputType, value.get(0));
                }

                check("Create sound", FMOD_System_CreateSound(core, stack.UTF8(tone.toString()), FMOD_DEFAULT, null, pointer));
                long sound = pointer.get(0);
                check("Play sound", FMOD_System_PlaySound(core, sound, 0, 0, pointer));
                long channel = pointer.get(0);
                long deadline = System.nanoTime() + 4_000_000_000L;
                int position;
                do {
                    update(studio);
                    check("Read playback position", FMOD_Channel_GetPosition(channel, value, FMOD_TIMEUNIT_MS));
                    position = value.get(0);
                } while (position < 1000 && System.nanoTime() < deadline);
                if (position < 1000) {
                    throw new IllegalStateException("Playback stalled at " + position + " ms");
                }
                System.out.printf("Channel reached %d ms of the 1500 ms tone%n", position);
                // 位置超过一秒后再更新约 600 ms，让 1.5 秒测试音播完。
                for (int i = 0; i < 30; i++) {
                    update(studio);
                }
                check("Release sound", FMOD_Sound_Release(sound));
            } finally {
                check("Release Studio system", FMOD_Studio_System_Release(studio));
            }
        }
    }

    /** 推进一次 Studio 更新，并为混音线程留出约一个游戏 tick 的时间。 */
    private static void update(long studio) throws InterruptedException {
        check("Studio update", FMOD_Studio_System_Update(studio));
        Thread.sleep(20);
    }

    /** 读取 FMOD 输出的 WAV，确认至少半秒的样本包含有效信号。 */
    private static void checkRenderedTone(Path output) throws Exception {
        try (AudioInputStream wav = AudioSystem.getAudioInputStream(output.toFile())) {
            AudioFormat source = wav.getFormat();
            // 统一转为小端 PCM16，便于对不同混音格式使用相同的幅度阈值。
            AudioFormat pcm16 = new AudioFormat(source.getSampleRate(), 16, source.getChannels(), true, false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(pcm16, wav)) {
                ShortBuffer samples = ByteBuffer.wrap(pcm.readAllBytes()).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
                int active = 0;
                int peak = 0;
                while (samples.hasRemaining()) {
                    int amplitude = Math.abs((int) samples.get());
                    peak = Math.max(peak, amplitude);
                    if (amplitude > 100) {
                        active++;
                    }
                }
                if (active < source.getSampleRate() * source.getChannels() / 2) {
                    throw new IllegalStateException("WAV has insufficient signal: " + active + " active samples");
                }
                System.out.printf("Verified mixed WAV: %s (%d active samples, peak=%d)%n", output, active, peak);
            }
        }
    }

    /** 在首个 FMOD API 调用失败时报告操作名和返回码。 */
    private static void check(String operation, int result) {
        if (result != FMOD_OK) {
            throw new IllegalStateException(operation + " returned FMOD result " + result);
        }
    }
}
