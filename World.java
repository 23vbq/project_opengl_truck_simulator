import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.jogamp.opengl.GL2;

public class World {

    private final HeightMap heightMap;
    private final Truck truck;
    private final List<Tree> trees;
    private final float inverseHeightScale;

    public World() {
        this.heightMap = new HeightMap();
        this.truck = new Truck();
        this.trees = new ArrayList<Tree>();
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
        truck.update(heightMap, trees);
    }

    public void setControls(boolean forward, boolean backward, boolean left, boolean right) {
        truck.setControls(forward, backward, left, right);
    }

    public void draw(GL2 gl) {
        drawTerrain(gl);
        drawTrees(gl);
        truck.draw(gl);
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
