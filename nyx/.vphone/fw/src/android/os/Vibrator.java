package android.os;

/** Vibrator that records requests for assertions. */
public class Vibrator {
    public boolean hasVibrator() { return true; }
    public void vibrate(long milliseconds) { android.test.Services.VIBRATIONS.add(milliseconds); }
    public void vibrate(long[] pattern, int repeat) { android.test.Services.VIBRATIONS.add(pattern[0]); }
    public void vibrate(VibrationEffect effect) { if (effect != null) android.test.Services.VIBRATIONS.add(effect.duration); }
    public void cancel() {}
}
