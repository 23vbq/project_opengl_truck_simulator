import com.jogamp.opengl.GL2;

public class World {

    private final HeightMap heightMap;
    private final float inverseHeightScale;

    public World() {
        this.heightMap = new HeightMap();
        this.inverseHeightScale = 1.0f / Math.max(0.0001f, heightMap.getHeightScale());
    }

    public HeightMap getHeightMap() {
        return heightMap;
    }

    public void draw(GL2 gl) {
        drawTerrain(gl);
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
