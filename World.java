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

    public World() {
        this.heightMap = new HeightMap();
        this.truck = new Truck();
        this.trees = new ArrayList<Tree>();
        this.road = new Road(heightMap);
        this.inverseHeightScale = 1.0f / Math.max(0.0001f, heightMap.getHeightScale());

        float center = (heightMap.getSize() - 1) * 0.5f;
        truck.setPosition(center, center);
        generateTrees(center, center);
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

    public void draw(GL2 gl) {
        long time = System.currentTimeMillis();
        drawTerrain(gl);
        road.draw(gl, heightMap, time);
        drawTrees(gl);
        truck.draw(gl);
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
