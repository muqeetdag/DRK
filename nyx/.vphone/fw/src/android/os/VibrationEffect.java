package android.os;

/** VibrationEffect: one-shot and waveform. */
public class VibrationEffect {

    public final long duration;
    public final long[] timings;
    public final int amplitude;

    private VibrationEffect(long duration, long[] timings, int amplitude) {
        this.duration = duration;
        this.timings = timings;
        this.amplitude = amplitude;
    }

    public static VibrationEffect createOneShot(long milliseconds, int amplitude) {
        return new VibrationEffect(milliseconds, null, amplitude);
    }

    public static VibrationEffect createWaveform(long[] timings, int repeat) {
        return new VibrationEffect(timings.length == 0 ? 0 : timings[0], timings, -1);
    }

    public static VibrationEffect createWaveform(long[] timings, int[] amplitudes, int repeat) {
        return createWaveform(timings, repeat);
    }

    public static final int DEFAULT_AMPLITUDE = -1;
}
