import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.jogamp.opengl.GL2;

public class World {

    private final HeightMap heightMap;
    private final Truck truck;
    private final List<Tree> trees;
    private final Road road;
    private final float inverseHeightScale;
    private float skyPhase;

    private static final int RAIN_PARTICLE_COUNT = 680;
    private static final int WIND_STREAK_COUNT = 190;
    private static final int CLOUD_COUNT = 48;
    private static final float CLOUD_ZONE_PADDING = 220.0f;
    private final float[] rainX;
    private final float[] rainY;
    private final float[] rainZ;
    private final float[] rainSpeed;
    private final float[] windStreakX;
    private final float[] windStreakY;
    private final float[] windStreakZ;
    private final float[] windStreakLife;
    private final float[] windStreakSpeed;
    private final float[] windStreakLength;
    private final float[] cloudX;
    private final float[] cloudY;
    private final float[] cloudZ;
    private final float[] cloudSize;
    private final float[] cloudSpeed;
    private final float[] cloudAlpha;
    private final Random rainRandom;
    private final Random cloudRandom;
    private long lastRainUpdateNanos;
    private long lastWindUpdateNanos;
    private long lastCloudUpdateNanos;
    private boolean rainEnabled;

    public World() {
        this.heightMap = new HeightMap();
        this.truck = new Truck();
        this.trees = new ArrayList<Tree>();
        this.road = new Road(heightMap);
        this.inverseHeightScale = 1.0f / Math.max(0.0001f, heightMap.getHeightScale());
        this.rainX = new float[RAIN_PARTICLE_COUNT];
        this.rainY = new float[RAIN_PARTICLE_COUNT];
        this.rainZ = new float[RAIN_PARTICLE_COUNT];
        this.rainSpeed = new float[RAIN_PARTICLE_COUNT];
        this.windStreakX = new float[WIND_STREAK_COUNT];
        this.windStreakY = new float[WIND_STREAK_COUNT];
        this.windStreakZ = new float[WIND_STREAK_COUNT];
        this.windStreakLife = new float[WIND_STREAK_COUNT];
        this.windStreakSpeed = new float[WIND_STREAK_COUNT];
        this.windStreakLength = new float[WIND_STREAK_COUNT];
        this.cloudX = new float[CLOUD_COUNT];
        this.cloudY = new float[CLOUD_COUNT];
        this.cloudZ = new float[CLOUD_COUNT];
        this.cloudSize = new float[CLOUD_COUNT];
        this.cloudSpeed = new float[CLOUD_COUNT];
        this.cloudAlpha = new float[CLOUD_COUNT];
        this.rainRandom = new Random(2026);
        this.cloudRandom = new Random(1701);
        this.rainEnabled = false;
        this.skyPhase = 0.25f;
        this.lastRainUpdateNanos = System.nanoTime();
        this.lastWindUpdateNanos = System.nanoTime();
        this.lastCloudUpdateNanos = System.nanoTime();

        float center = (heightMap.getSize() - 1) * 0.5f;
        truck.setPosition(center, center);
        generateTrees(center, center);
        initializeRainParticles();
        initializeWindStreaks();
        initializeClouds();
    }

    public HeightMap getHeightMap() {
        return heightMap;
    }

    public Truck getTruck() {
        return truck;
    }

    public void update() {
        truck.update(heightMap, trees, road);
    }

    public void setControls(boolean forward, boolean backward, boolean left, boolean right) {
        truck.setControls(forward, backward, left, right);
    }

    public void setRainEnabled(boolean enabled) {
        rainEnabled = enabled;
    }

    public boolean isRainEnabled() {
        return rainEnabled;
    }

    public void setSkyPhase(float phase) {
        skyPhase = phase;
    }

    public void draw(GL2 gl) {
        long time = System.currentTimeMillis();
        drawSkyGradient(gl);
        drawTerrain(gl);
        road.draw(gl, heightMap, time);
        drawTrees(gl);
        drawClouds(gl);
        drawWindStreaks(gl);
        drawRain(gl);
        truck.draw(gl);
    }

    private void drawSkyGradient(GL2 gl) {
        float[] horizonColor = sampleSkyColor(skyPhase, false);
        float[] zenithColor = sampleSkyColor(skyPhase, true);

        float centerX = truck.getX();
        float centerY = 6.0f;
        float centerZ = truck.getZ();
        float radius = 520.0f;
        int stacks = 14;
        int slices = 48;

        boolean fogEnabled = gl.glIsEnabled(GL2.GL_FOG);

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glDisable(GL2.GL_FOG);
        gl.glDepthMask(false);

        gl.glPushMatrix();
        gl.glTranslatef(centerX, centerY, centerZ);

        for (int stack = 0; stack < stacks; stack++) {
            float t0 = (float) stack / (float) stacks;
            float t1 = (float) (stack + 1) / (float) stacks;

            float elev0 = t0 * (float) (Math.PI * 0.5f);
            float elev1 = t1 * (float) (Math.PI * 0.5f);

            float y0 = (float) Math.sin(elev0) * radius;
            float y1 = (float) Math.sin(elev1) * radius;
            float ring0 = (float) Math.cos(elev0) * radius;
            float ring1 = (float) Math.cos(elev1) * radius;

            float blend0 = (float) Math.pow(t0, 0.70f);
            float blend1 = (float) Math.pow(t1, 0.70f);
            float[] c0 = mixColor(horizonColor, zenithColor, blend0);
            float[] c1 = mixColor(horizonColor, zenithColor, blend1);

            gl.glBegin(GL2.GL_TRIANGLE_STRIP);
            for (int slice = 0; slice <= slices; slice++) {
                float a = (float) (slice * Math.PI * 2.0f / slices);
                float cosA = (float) Math.cos(a);
                float sinA = (float) Math.sin(a);

                gl.glColor3f(c1[0], c1[1], c1[2]);
                gl.glVertex3f(cosA * ring1, y1, sinA * ring1);

                gl.glColor3f(c0[0], c0[1], c0[2]);
                gl.glVertex3f(cosA * ring0, y0, sinA * ring0);
            }
            gl.glEnd();
        }

        gl.glPopMatrix();

        gl.glDepthMask(true);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        if (fogEnabled) {
            gl.glEnable(GL2.GL_FOG);
        }
        gl.glEnable(GL2.GL_LIGHTING);
    }

    private float[] sampleSkyColor(float phase, boolean zenith) {
        float[][] horizonKeys = {
            { 0.84f, 0.62f, 0.45f },
            { 0.61f, 0.78f, 0.96f },
            { 0.95f, 0.55f, 0.32f },
            { 0.05f, 0.07f, 0.14f },
            { 0.84f, 0.62f, 0.45f }
        };

        float[][] zenithKeys = {
            { 0.52f, 0.56f, 0.74f },
            { 0.26f, 0.52f, 0.90f },
            { 0.45f, 0.25f, 0.44f },
            { 0.01f, 0.02f, 0.07f },
            { 0.52f, 0.56f, 0.74f }
        };

        float[] keyTimes = { 0.00f, 0.25f, 0.50f, 0.75f, 1.00f };
        float[][] keys = zenith ? zenithKeys : horizonKeys;

        int index = 0;
        while (index < keyTimes.length - 2 && phase > keyTimes[index + 1]) {
            index++;
        }

        float start = keyTimes[index];
        float end = keyTimes[index + 1];
        float t = (phase - start) / Math.max(0.0001f, end - start);

        float[] a = keys[index];
        float[] b = keys[index + 1];
        return new float[] {
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t
        };
    }

    private float[] mixColor(float[] a, float[] b, float t) {
        return new float[] {
            a[0] + (b[0] - a[0]) * t,
            a[1] + (b[1] - a[1]) * t,
            a[2] + (b[2] - a[2]) * t
        };
    }

    private void initializeClouds() {
        for (int i = 0; i < CLOUD_COUNT; i++) {
            resetCloud(i, true);
        }
    }

    private void resetCloud(int index, boolean randomizeY) {
        float size = heightMap.getSize();
        float center = (size - 1) * 0.5f;
        float span = size + CLOUD_ZONE_PADDING * 2.0f;

        cloudX[index] = center - span * 0.5f + cloudRandom.nextFloat() * span;
        cloudZ[index] = center - span * 0.5f + cloudRandom.nextFloat() * span;
        cloudY[index] = randomizeY ? (18.0f + cloudRandom.nextFloat() * 10.0f) : cloudY[index];
        cloudSize[index] = 5.0f + cloudRandom.nextFloat() * 5.8f;
        cloudSpeed[index] = 0.45f + cloudRandom.nextFloat() * 0.75f;
        cloudAlpha[index] = 0.16f + cloudRandom.nextFloat() * 0.14f;
    }

    private void updateClouds() {
        long now = System.nanoTime();
        float dt = (now - lastCloudUpdateNanos) / 1_000_000_000.0f;
        lastCloudUpdateNanos = now;

        dt = Math.max(0.001f, Math.min(dt, 0.05f));

        float windX = 3.6f + (float) Math.sin(now * 0.0000000007f) * 1.3f;
        float windZ = 1.0f + (float) Math.cos(now * 0.0000000005f) * 0.8f;

        float size = heightMap.getSize();
        float center = (size - 1) * 0.5f;
        float halfSpan = size * 0.5f + CLOUD_ZONE_PADDING;
        float minBound = center - halfSpan;
        float maxBound = center + halfSpan;
        float wrapSpan = (maxBound - minBound);

        for (int i = 0; i < CLOUD_COUNT; i++) {
            cloudX[i] += windX * cloudSpeed[i] * dt;
            cloudZ[i] += windZ * cloudSpeed[i] * dt;

            float bob = (float) Math.sin(now * 0.0000000009f + i * 0.41f) * 0.0014f;
            cloudY[i] = Math.max(16.5f, Math.min(30.0f, cloudY[i] + bob));

            if (cloudX[i] > maxBound) {
                cloudX[i] -= wrapSpan;
            } else if (cloudX[i] < minBound) {
                cloudX[i] += wrapSpan;
            }

            if (cloudZ[i] > maxBound) {
                cloudZ[i] -= wrapSpan;
            } else if (cloudZ[i] < minBound) {
                cloudZ[i] += wrapSpan;
            }
        }
    }

    private void drawClouds(GL2 gl) {
        updateClouds();

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);

        for (int i = 0; i < CLOUD_COUNT; i++) {
            float x = cloudX[i];
            float y = cloudY[i];
            float z = cloudZ[i];
            float size = cloudSize[i];
            float alpha = cloudAlpha[i];

            drawCloudPuff(gl, x, y, z, size, alpha);
        }

        gl.glDepthMask(true);
        gl.glDisable(GL2.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
    }

    private void drawCloudPuff(GL2 gl, float x, float y, float z, float size, float alpha) {
        gl.glPushMatrix();
        gl.glTranslatef(x, y, z);

        gl.glColor4f(0.88f, 0.90f, 0.94f, alpha);
        gl.glPushMatrix();
        gl.glScalef(size * 1.00f, size * 0.32f, size * 0.74f);
        drawSphere(gl, 1.0f, 10, 12);
        gl.glPopMatrix();

        gl.glColor4f(0.90f, 0.92f, 0.96f, alpha * 0.95f);
        gl.glPushMatrix();
        gl.glTranslatef(-size * 0.40f, 0.20f, size * 0.08f);
        gl.glScalef(size * 0.70f, size * 0.25f, size * 0.54f);
        drawSphere(gl, 1.0f, 9, 11);
        gl.glPopMatrix();

        gl.glColor4f(0.86f, 0.88f, 0.92f, alpha * 0.88f);
        gl.glPushMatrix();
        gl.glTranslatef(size * 0.44f, 0.16f, -size * 0.06f);
        gl.glScalef(size * 0.66f, size * 0.24f, size * 0.50f);
        drawSphere(gl, 1.0f, 9, 11);
        gl.glPopMatrix();

        gl.glPopMatrix();
    }

    private void initializeRainParticles() {
        for (int i = 0; i < RAIN_PARTICLE_COUNT; i++) {
            resetRainParticle(i, true);
        }
    }

    private void initializeWindStreaks() {
        for (int i = 0; i < WIND_STREAK_COUNT; i++) {
            resetWindStreak(i, true);
            windStreakLife[i] *= rainRandom.nextFloat();
        }
    }

    private void resetRainParticle(int index, boolean randomHeight) {
        float size = heightMap.getSize();
        rainX[index] = 2.0f + rainRandom.nextFloat() * (size - 4.0f);
        rainZ[index] = 2.0f + rainRandom.nextFloat() * (size - 4.0f);

        float terrainY = heightMap.getHeight(rainX[index], rainZ[index]);
        float spawnHeight = randomHeight ? (10.0f + rainRandom.nextFloat() * 45.0f) : 42.0f;
        rainY[index] = terrainY + spawnHeight;
        rainSpeed[index] = 26.0f + rainRandom.nextFloat() * 22.0f;
    }

    private void resetWindStreak(int index, boolean randomHeight) {
        float size = heightMap.getSize();
        windStreakX[index] = 2.0f + rainRandom.nextFloat() * (size - 4.0f);
        windStreakZ[index] = 2.0f + rainRandom.nextFloat() * (size - 4.0f);

        float terrainY = heightMap.getHeight(windStreakX[index], windStreakZ[index]);
        float spawnHeight = randomHeight ? (6.0f + rainRandom.nextFloat() * 38.0f) : (12.0f + rainRandom.nextFloat() * 28.0f);
        windStreakY[index] = terrainY + spawnHeight;
        windStreakLife[index] = 1.4f + rainRandom.nextFloat() * 2.8f;
        windStreakSpeed[index] = 8.0f + rainRandom.nextFloat() * 13.0f;
        windStreakLength[index] = 1.5f + rainRandom.nextFloat() * 2.8f;
    }

    private void updateRain() {
        long now = System.nanoTime();
        float dt = (now - lastRainUpdateNanos) / 1_000_000_000.0f;
        lastRainUpdateNanos = now;

        dt = Math.max(0.001f, Math.min(dt, 0.05f));

        float windX = (float) Math.sin(now * 0.0000000012f) * 1.8f;
        float windZ = (float) Math.cos(now * 0.0000000014f) * 1.3f;
        float size = heightMap.getSize();

        for (int i = 0; i < RAIN_PARTICLE_COUNT; i++) {
            rainY[i] -= rainSpeed[i] * dt;
            rainX[i] += windX * dt;
            rainZ[i] += windZ * dt;

            if (rainX[i] < 1.0f || rainX[i] > size - 2.0f || rainZ[i] < 1.0f || rainZ[i] > size - 2.0f) {
                resetRainParticle(i, true);
                continue;
            }

            float groundY = heightMap.getHeight(rainX[i], rainZ[i]);
            if (rainY[i] <= groundY + 0.2f) {
                resetRainParticle(i, false);
            }
        }
    }

    private void updateWindStreaks() {
        long now = System.nanoTime();
        float dt = (now - lastWindUpdateNanos) / 1_000_000_000.0f;
        lastWindUpdateNanos = now;

        dt = Math.max(0.001f, Math.min(dt, 0.05f));

        float baseWindX = (float) Math.sin(now * 0.0000000012f) * 1.8f;
        float baseWindZ = (float) Math.cos(now * 0.0000000014f) * 1.3f;
        float size = heightMap.getSize();

        for (int i = 0; i < WIND_STREAK_COUNT; i++) {
            float gust = 0.7f + (float) Math.sin(now * 0.0000000022f + i * 0.37f) * 0.35f;

            windStreakX[i] += baseWindX * windStreakSpeed[i] * gust * dt;
            windStreakZ[i] += baseWindZ * windStreakSpeed[i] * gust * dt;
            windStreakY[i] += (float) Math.sin(now * 0.000000003f + i * 0.2f) * 0.18f * dt;
            windStreakLife[i] -= dt;

            if (windStreakX[i] < 1.0f || windStreakX[i] > size - 2.0f || windStreakZ[i] < 1.0f || windStreakZ[i] > size - 2.0f
                    || windStreakLife[i] <= 0.0f) {
                resetWindStreak(i, false);
            }
        }
    }

    private void drawWindStreaks(GL2 gl) {
        updateWindStreaks();

        long now = System.nanoTime();
        float windDirX = (float) Math.sin(now * 0.0000000012f) * 1.8f;
        float windDirZ = (float) Math.cos(now * 0.0000000014f) * 1.3f;
        float dirLen = (float) Math.sqrt(windDirX * windDirX + windDirZ * windDirZ);
        if (dirLen < 0.0001f) {
            windDirX = 1.0f;
            windDirZ = 0.0f;
            dirLen = 1.0f;
        }
        windDirX /= dirLen;
        windDirZ /= dirLen;

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glLineWidth(2.6f);

        gl.glBegin(GL2.GL_LINES);
        for (int i = 0; i < WIND_STREAK_COUNT; i++) {
            float lifeRatio = Math.max(0.0f, Math.min(1.0f, windStreakLife[i] / 4.0f));
            float alpha = 0.10f + lifeRatio * 0.23f;
            float length = windStreakLength[i];

            float x1 = windStreakX[i];
            float y1 = windStreakY[i];
            float z1 = windStreakZ[i];
            float x2 = x1 - windDirX * length;
            float y2 = y1 + 0.06f;
            float z2 = z1 - windDirZ * length;

            gl.glColor4f(0.86f, 0.92f, 1.0f, alpha);
            gl.glVertex3f(x1, y1, z1);
            gl.glVertex3f(x2, y2, z2);
        }
        gl.glEnd();

        gl.glLineWidth(1.0f);
        gl.glDisable(GL2.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
    }

    private void drawRain(GL2 gl) {
        if (!rainEnabled) {
            return;
        }

        updateRain();

        long now = System.nanoTime();
        float windX = (float) Math.sin(now * 0.0000000012f) * 1.8f;
        float windZ = (float) Math.cos(now * 0.0000000014f) * 1.3f;
        float tailY = 0.75f;

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glColor4f(0.74f, 0.84f, 1.0f, 0.48f);

        gl.glBegin(GL2.GL_LINES);
        for (int i = 0; i < RAIN_PARTICLE_COUNT; i++) {
            gl.glVertex3f(rainX[i], rainY[i], rainZ[i]);
            gl.glVertex3f(rainX[i] - windX * 0.06f, rainY[i] + tailY, rainZ[i] - windZ * 0.06f);
        }
        gl.glEnd();

        gl.glDisable(GL2.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
    }

    public void drawCelestialBodies(GL2 gl, float phase) {
        float sunPhase = phase;
        float sunAzimuth = (float) Math.PI + sunPhase * (float) Math.PI * 2.0f;
        float sunElevation = (float) (Math.sin(sunPhase * Math.PI) * Math.PI * 0.45f);
        float sunDistance = 280.0f;
        float sunX = (float) (Math.cos(sunAzimuth) * Math.cos(sunElevation)) * sunDistance;
        float sunY = (float) Math.sin(sunElevation) * sunDistance;
        float sunZ = (float) (Math.sin(sunAzimuth) * Math.cos(sunElevation)) * sunDistance;

        float sunIntensity = Math.max(0.0f, (float) Math.sin(sunPhase * Math.PI));
        float moonIntensity = Math.max(0.0f, (float) Math.sin((sunPhase - 0.5f) * Math.PI));

        if (sunIntensity > 0.05f) {
            drawSun(gl, sunX, sunY, sunZ, sunIntensity);
        }

        if (moonIntensity > 0.05f) {
            float moonX = -sunX;
            float moonY = sunY;
            float moonZ = -sunZ;
            drawMoon(gl, moonX, moonY, moonZ, moonIntensity);
        }
    }

    private void drawSun(GL2 gl, float x, float y, float z, float intensity) {
        gl.glDisable(GL2.GL_FOG);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_BLEND);
        gl.glPushMatrix();
        gl.glTranslatef(x, y, z);

        long time = System.currentTimeMillis();
        float pulse = 1.0f + (float) Math.sin(time * 0.001f) * 0.15f;
        float sunR = (4.0f + (float) Math.sin(time * 0.0005f) * 0.5f) * pulse;
        float sunG = (2.4f + (float) Math.sin(time * 0.0003f + 2.0f) * 0.4f) * pulse;

        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE);
        
        // Core sphere - super bright
        gl.glColor4f(sunR * intensity * 1.4f, sunG * intensity * 1.0f, 0.6f * intensity, 1.0f);
        drawSphere(gl, 4.2f, 20, 16);

        // Intense inner halo
        gl.glColor4f(sunR * intensity * 1.1f, sunG * intensity * 0.8f, 0.4f * intensity, 0.85f);
        drawSphere(gl, 8.5f, 18, 14);

        // Mid-range halo
        gl.glColor4f(sunR * intensity * 0.8f, sunG * intensity * 0.5f, 0.25f * intensity, 0.65f);
        drawSphere(gl, 13.5f, 16, 12);

        // Outer halo
        gl.glColor4f(sunR * intensity * 0.5f, sunG * intensity * 0.3f, 0.15f * intensity, 0.45f);
        drawSphere(gl, 19.5f, 14, 10);

        // Far halo
        gl.glColor4f(sunR * intensity * 0.3f, sunG * intensity * 0.15f, 0.08f * intensity, 0.28f);
        drawSphere(gl, 26.5f, 12, 8);

        // Ultra-far glow
        gl.glColor4f(sunR * intensity * 0.15f, sunG * intensity * 0.08f, 0.04f * intensity, 0.12f);
        drawSphere(gl, 34.0f, 10, 6);

        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glPopMatrix();
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_FOG);
    }

    private void drawMoon(GL2 gl, float x, float y, float z, float intensity) {
        gl.glDisable(GL2.GL_FOG);
        gl.glDisable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_BLEND);
        gl.glPushMatrix();
        gl.glTranslatef(x, y, z);

        long time = System.currentTimeMillis();
        float moonPulse = 1.0f + (float) Math.sin(time * 0.0008f) * 0.08f;

        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE);
        
        // Bright core
        gl.glColor4f(1.1f * intensity * moonPulse, 1.08f * intensity * moonPulse, 0.95f * intensity * moonPulse, 1.0f);
        drawSphere(gl, 4.0f, 20, 16);

        // Inner glow
        gl.glColor4f(1.0f * intensity, 0.98f * intensity, 0.88f * intensity, 0.75f);
        drawSphere(gl, 8.5f, 18, 14);

        // Mid halo
        gl.glColor4f(0.95f * intensity, 0.93f * intensity, 0.82f * intensity, 0.55f);
        drawSphere(gl, 14.0f, 16, 12);

        // Outer halo
        gl.glColor4f(0.88f * intensity, 0.86f * intensity, 0.75f * intensity, 0.35f);
        drawSphere(gl, 20.0f, 14, 10);

        // Far glow
        gl.glColor4f(0.80f * intensity, 0.78f * intensity, 0.68f * intensity, 0.15f);
        drawSphere(gl, 27.0f, 12, 8);

        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glPopMatrix();
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_FOG);
    }

    private void drawSphere(GL2 gl, float radius, int stacks, int slices) {
        gl.glBegin(GL2.GL_TRIANGLE_STRIP);
        for (int i = 0; i <= stacks; i++) {
            float stackAngle = (float) (Math.PI * i / stacks);
            float stackSin = (float) Math.sin(stackAngle);
            float stackCos = (float) Math.cos(stackAngle);

            for (int j = 0; j <= slices; j++) {
                float sliceAngle = (float) (2.0f * Math.PI * j / slices);
                float sliceSin = (float) Math.sin(sliceAngle);
                float sliceCos = (float) Math.cos(sliceAngle);

                float nx = sliceCos * stackSin;
                float ny = stackCos;
                float nz = sliceSin * stackSin;

                gl.glVertex3f(nx * radius, ny * radius, nz * radius);

                if (i < stacks) {
                    float nextStackAngle = (float) (Math.PI * (i + 1) / stacks);
                    float nextStackSin = (float) Math.sin(nextStackAngle);
                    float nextStackCos = (float) Math.cos(nextStackAngle);
                    nx = sliceCos * nextStackSin;
                    ny = nextStackCos;
                    nz = sliceSin * nextStackSin;
                    gl.glVertex3f(nx * radius, ny * radius, nz * radius);
                }
            }
        }
        gl.glEnd();
    }

    private void drawTrees(GL2 gl) {
        for (int i = 0; i < trees.size(); i++) {
            trees.get(i).draw(gl);
        }
    }

    private void generateTrees(float truckSpawnX, float truckSpawnZ) {
        Random random = new Random(314159);
        int targetCount = 280;
        int attempts = 0;
        int maxAttempts = 9000;
        int size = heightMap.getSize();

        while (trees.size() < targetCount && attempts < maxAttempts) {
            attempts++;
            float x = 2.0f + random.nextFloat() * (size - 4.0f);
            float z = 2.0f + random.nextFloat() * (size - 4.0f);

            float dx = x - truckSpawnX;
            float dz = z - truckSpawnZ;
            if (dx * dx + dz * dz < 26.0f * 26.0f) {
                continue;
            }

            float normalized = heightMap.getHeight((int) x, (int) z) * inverseHeightScale;
            if (normalized < 0.20f || normalized > 0.78f) {
                continue;
            }

            if (isTooSteep(x, z)) {
                continue;
            }
            
            // Omij drogi - drzewa muszą być co najmniej 10 jednostek od drogi
            if (road.getDistanceToRoad(x, z) < 10.0f) {
                continue;
            }

            float y = heightMap.getHeight(x, z);
            float height = 2.6f + random.nextFloat() * 2.3f;
            float radius = 0.75f + random.nextFloat() * 0.55f;
            float greenTint = -0.08f + random.nextFloat() * 0.16f;
            float rotationY = random.nextFloat() * 360.0f;

            trees.add(new Tree(x, y, z, height, radius, greenTint, rotationY));
        }
    }

    private boolean isTooSteep(float x, float z) {
        float step = 1.1f;
        float hCenter = heightMap.getHeight(x, z);
        float h1 = heightMap.getHeight(x + step, z);
        float h2 = heightMap.getHeight(x - step, z);
        float h3 = heightMap.getHeight(x, z + step);
        float h4 = heightMap.getHeight(x, z - step);

        float maxDelta = Math.max(Math.max(Math.abs(hCenter - h1), Math.abs(hCenter - h2)),
                Math.max(Math.abs(hCenter - h3), Math.abs(hCenter - h4)));

        return maxDelta > 1.7f;
    }

    private void drawTerrain(GL2 gl) {
        int size = heightMap.getSize();

        gl.glBegin(GL2.GL_TRIANGLES);
        for (int x = 0; x < size - 1; x++) {
            for (int z = 0; z < size - 1; z++) {
                float y00 = heightMap.getHeight(x, z);
                float y10 = heightMap.getHeight(x + 1, z);
                float y01 = heightMap.getHeight(x, z + 1);
                float y11 = heightMap.getHeight(x + 1, z + 1);

                drawTriangle(gl, x, y00, z, x, y01, z + 1, x + 1, y10, z);
                drawTriangle(gl, x + 1, y10, z, x, y01, z + 1, x + 1, y11, z + 1);
            }
        }
        gl.glEnd();
    }

    private void drawTriangle(GL2 gl, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {
        float[] normal = calculateNormal(ax, ay, az, bx, by, bz, cx, cy, cz);
        gl.glNormal3f(normal[0], normal[1], normal[2]);

        setColorByNormalizedHeight(gl, ay * inverseHeightScale);
        gl.glVertex3f(ax, ay, az);

        setColorByNormalizedHeight(gl, by * inverseHeightScale);
        gl.glVertex3f(bx, by, bz);

        setColorByNormalizedHeight(gl, cy * inverseHeightScale);
        gl.glVertex3f(cx, cy, cz);
    }

    private void setColorByNormalizedHeight(GL2 gl, float normalizedHeight) {
        if (normalizedHeight < 0.1f) {
            gl.glColor3f(0.10f, 0.32f, 0.82f);
        } else if (normalizedHeight < 0.5f) {
            gl.glColor3f(0.18f, 0.60f, 0.20f);
        } else if (normalizedHeight < 0.8f) {
            gl.glColor3f(0.50f, 0.50f, 0.50f);
        } else {
            gl.glColor3f(0.95f, 0.95f, 0.95f);
        }
    }

    private float[] calculateNormal(float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz) {
        float ux = bx - ax;
        float uy = by - ay;
        float uz = bz - az;

        float vx = cx - ax;
        float vy = cy - ay;
        float vz = cz - az;

        float nx = uy * vz - uz * vy;
        float ny = uz * vx - ux * vz;
        float nz = ux * vy - uy * vx;

        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 0.0001f) {
            return new float[] { 0.0f, 1.0f, 0.0f };
        }

        return new float[] { nx / length, ny / length, nz / length };
    }
}
