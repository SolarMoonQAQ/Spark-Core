package cn.solarmoon.spark_core.sound;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;

public record SoundData(ByteBuffer byteBuffer, AudioFormat audioFormat)  {
}
