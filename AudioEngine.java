import java.util.Random;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;

public class AudioEngine {

    private static final float TWO_PI = (float) (Math.PI * 2.0);
    private static final float SAMPLE_RATE = 22050.0f;
    private static final int BUFFER_SAMPLES = 1024;

    private volatile boolean running;
    private volatile boolean enabled;

    private volatile float speed;
    private volatile float rpm;
    private volatile boolean accelerating;
    private volatile boolean reversing;
    private volatile boolean rainEnabled;
    private volatile boolean indicatorRequested;
    private volatile boolean indicatorBlinkOn;
    private volatile float brakeIntensity;

    private SourceDataLine line;
    private Thread audioThread;

    private float enginePhaseA;
    private float enginePhaseB;
    private float enginePhaseC;
    private float enginePhaseD;
    private float firingPhase;
    private float rumblePhase;
    private float engineSmooth;
    private float rainSmooth;
    private float brakeNoiseSmooth;
    private float turboSmooth;
    private float turboNoiseLow;
    private float turboWhistlePhase;

    private boolean lastBlinkOn;
    private float clickEnvelope;
    private float clickPhase;

    private final Random noiseRandom;

    public AudioEngine() {
        this.enabled = true;
        this.noiseRandom = new Random(424242);
    }

    public void start() {
        if (running) {
            return;
        }

        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try {
            line = AudioSystem.getSourceDataLine(format);
            int lineBufferBytes = BUFFER_SAMPLES * 2 * 8;
            line.open(format, lineBufferBytes);
            line.start();
        } catch (LineUnavailableException e) {
            running = false;
            line = null;
            return;
        }

        running = true;
        audioThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runAudioLoop();
            }
        }, "AudioEngineThread");
        audioThread.setDaemon(true);
        audioThread.start();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void update(float speed, float rpm, boolean accelerating, boolean reversing, boolean rainEnabled,
            boolean indicatorRequested, boolean indicatorBlinkOn, float brakeIntensity) {
        this.speed = speed;
        this.rpm = rpm;
        this.accelerating = accelerating;
        this.reversing = reversing;
        this.rainEnabled = rainEnabled;
        this.indicatorRequested = indicatorRequested;
        this.indicatorBlinkOn = indicatorBlinkOn;
        this.brakeIntensity = brakeIntensity;
    }

    public void shutdown() {
        running = false;
        if (audioThread != null) {
            try {
                audioThread.join(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            audioThread = null;
        }

        if (line != null) {
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }

    private void runAudioLoop() {
        byte[] pcm = new byte[BUFFER_SAMPLES * 2];

        while (running) {
            fillPcmBuffer(pcm, BUFFER_SAMPLES);
            if (line != null) {
                line.write(pcm, 0, pcm.length);
            }
        }
    }

    private void fillPcmBuffer(byte[] out, int samples) {
        float localSpeed = Math.abs(speed);
        boolean localAccelerating = accelerating;
        boolean localReversing = reversing;
        boolean localRainEnabled = rainEnabled;
        boolean localIndicatorRequested = indicatorRequested;
        boolean localIndicatorBlinkOn = indicatorBlinkOn;
        float localBrake = clamp(brakeIntensity, 0.0f, 1.0f);
        boolean localEnabled = enabled;

        if (localIndicatorRequested && localIndicatorBlinkOn && !lastBlinkOn) {
            clickEnvelope = 1.0f;
        }
        lastBlinkOn = localIndicatorBlinkOn && localIndicatorRequested;

        float localRpm = rpm;
        float throttle = localAccelerating ? 1.0f : (localReversing ? 0.75f : 0.25f);
        float rpmNormalized = clamp((localRpm - 650.0f) / (2600.0f - 650.0f), 0.0f, 1.0f);
        // Volume driven by real RPM
        float targetEngine = 0.22f + rpmNormalized * 0.62f + throttle * 0.18f;
        engineSmooth += (targetEngine - engineSmooth) * 0.035f;

        // Scania V8 profile: frequency and harmonics driven by real RPM
        float engineRevHz = 2.5f + rpmNormalized * 21.0f;
        float engineFreqA = engineRevHz;
        float engineFreqB = engineRevHz * 2.01f;
        float engineFreqC = engineRevHz * 3.02f;
        float engineFreqD = engineRevHz * 0.50f;
        float firingFreq = engineRevHz * 0.8f;

        float turboTarget = (0.005f + rpmNormalized * 0.015f)
            + (localAccelerating ? (0.02f + rpmNormalized * 0.04f) : 0.0f);
        if (localReversing) {
            turboTarget *= 0.6f;
        }
        turboSmooth += (turboTarget - turboSmooth) * 0.018f;

        float whistleLoad = clamp(turboSmooth * 4.2f + throttle * 0.04f, 0.0f, 1.0f);
        float turboWhistleFreq = 900.0f + whistleLoad * 1200.0f + rpmNormalized * 180.0f;

        float rainTarget = localRainEnabled ? 0.32f : 0.0f;
        rainSmooth += (rainTarget - rainSmooth) * 0.01f;

        for (int i = 0; i < samples; i++) {
            float sample = 0.0f;

            if (localEnabled) {
                enginePhaseA += (float) (TWO_PI * engineFreqA / SAMPLE_RATE);
                enginePhaseB += (float) (TWO_PI * engineFreqB / SAMPLE_RATE);
                enginePhaseC += (float) (TWO_PI * engineFreqC / SAMPLE_RATE);
                enginePhaseD += (float) (TWO_PI * engineFreqD / SAMPLE_RATE);
                firingPhase += (float) (TWO_PI * firingFreq / SAMPLE_RATE);
                rumblePhase += (float) (TWO_PI * (0.7f + rpmNormalized * 2.4f) / SAMPLE_RATE);
                if (enginePhaseA > TWO_PI) {
                    enginePhaseA -= TWO_PI;
                }
                if (enginePhaseB > TWO_PI) {
                    enginePhaseB -= TWO_PI;
                }
                if (enginePhaseC > TWO_PI) {
                    enginePhaseC -= TWO_PI;
                }
                if (enginePhaseD > TWO_PI) {
                    enginePhaseD -= TWO_PI;
                }
                if (firingPhase > TWO_PI) {
                    firingPhase -= TWO_PI;
                }
                if (rumblePhase > TWO_PI) {
                    rumblePhase -= TWO_PI;
                }

                // V8 cross-plane firing: 8 pulses per cycle with alternating short/long gaps
                // Offsets in radians: 0, π/4, π, 5π/4  (gaps: π/4, 3π/4, π/4, 3π/4...)
                float p1 = pulseFromPhase(firingPhase,               0.38f);
                float p2 = pulseFromPhase(firingPhase + 0.785f,      0.35f);
                float p3 = pulseFromPhase(firingPhase + (float)Math.PI,       0.38f);
                float p4 = pulseFromPhase(firingPhase + (float)Math.PI + 0.785f, 0.35f);

                float pulseMix = clamp((p1 + p2 + p3 + p4) * 0.55f, 0.0f, 1.2f);
                float gateDepth = 0.82f - rpmNormalized * 0.22f;
                float pulseGate = (1.0f - gateDepth) + gateDepth * pulseMix;

                float loping = 0.78f + 0.22f * (float) Math.sin(rumblePhase);
                float harmonic = (float) Math.sin(enginePhaseA) * 0.48f
                        + (float) Math.sin(enginePhaseB) * 0.23f
                        + (float) Math.sin(enginePhaseC) * 0.12f
                        + (float) Math.sin(enginePhaseD) * 0.22f;
                float combustion = (p1 * 0.60f + p2 * 0.55f + p3 * 0.60f + p4 * 0.55f) - 0.24f;

                float engineTone = (harmonic + combustion) * loping * pulseGate;
                float driveGain = 0.90f + throttle * 0.35f + rpmNormalized * 0.20f;
                float engine = softClip(engineTone * engineSmooth * driveGain);
                sample += engine * 1.20f;

                float thump = ((p1 * 0.85f + p2 * 0.78f + p3 * 0.85f + p4 * 0.78f) - 0.20f) * (0.55f + throttle * 0.35f);
                sample += softClip(thump) * 0.50f;

                float rainNoise = (noiseRandom.nextFloat() * 2.0f - 1.0f);
                brakeNoiseSmooth = brakeNoiseSmooth * 0.86f + rainNoise * 0.14f;
                sample += brakeNoiseSmooth * rainSmooth * 0.45f;

                float turboNoise = (noiseRandom.nextFloat() * 2.0f - 1.0f);
                turboNoiseLow = turboNoiseLow * 0.90f + turboNoise * 0.10f;
                float turboHiss = turboNoise - turboNoiseLow;
                sample += turboHiss * turboSmooth * 0.06f;

                turboWhistlePhase += (float) (TWO_PI * turboWhistleFreq / SAMPLE_RATE);
                if (turboWhistlePhase > TWO_PI) {
                    turboWhistlePhase -= TWO_PI;
                }

                float whistleTone = (float) Math.sin(turboWhistlePhase);
                float whistle = whistleTone * (0.001f + whistleLoad * 0.012f);
                sample += whistle;

                if (localBrake > 0.05f) {
                    float hiss = noiseRandom.nextFloat() * 2.0f - 1.0f;
                    sample += hiss * (0.04f + localBrake * 0.09f);
                }

                if (clickEnvelope > 0.0005f) {
                    clickPhase += (float) (2.0f * Math.PI * 1180.0f / SAMPLE_RATE);
                    if (clickPhase > Math.PI * 2.0f) {
                        clickPhase -= (float) (Math.PI * 2.0f);
                    }
                    float tick = (float) Math.sin(clickPhase) * clickEnvelope;
                    sample += tick * 0.33f;
                    clickEnvelope *= 0.965f;
                }
            }

            short pcmSample = (short) (clamp(sample, -1.0f, 1.0f) * 32767.0f);
            int outIndex = i * 2;
            out[outIndex] = (byte) (pcmSample & 0xff);
            out[outIndex + 1] = (byte) ((pcmSample >> 8) & 0xff);
        }
    }

    private float softClip(float x) {
        return x / (1.0f + Math.abs(x));
    }

    private float pulseFromPhase(float phase, float widthRad) {
        float wrapped = phase;
        while (wrapped > TWO_PI) {
            wrapped -= TWO_PI;
        }
        while (wrapped < 0.0f) {
            wrapped += TWO_PI;
        }

        float distance = Math.min(wrapped, TWO_PI - wrapped);
        float normalized = 1.0f - distance / Math.max(0.0001f, widthRad);
        if (normalized <= 0.0f) {
            return 0.0f;
        }

        return normalized * normalized * normalized;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
