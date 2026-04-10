import com.jogamp.opengl.GL2;

public class Tree {

    private final float x;
    private final float y;
    private final float z;
    private final float height;
    private final float radius;
    private final float greenTint;
    private final float rotationY;

    public Tree(float x, float y, float z, float height, float radius) {
        this(x, y, z, height, radius, 0.0f, 0.0f);
    }

    public Tree(float x, float y, float z, float height, float radius, float greenTint, float rotationY) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.height = height;
        this.radius = radius;
        this.greenTint = greenTint;
        this.rotationY = rotationY;
    }

    public void draw(GL2 gl) {
        float trunkHeight = height * 0.36f;
        float trunkRadius = radius * 0.24f;

        gl.glPushMatrix();
        gl.glTranslatef(x, y, z);
        gl.glRotatef(rotationY, 0.0f, 1.0f, 0.0f);

        gl.glColor3f(0.38f, 0.24f, 0.12f);
        drawCylinder(gl, trunkRadius, trunkHeight, 10);

        float lowerConeBaseY = trunkHeight * 0.52f;
        float upperConeBaseY = trunkHeight * 1.02f;

        float baseGreen = 0.46f + greenTint;
        float darkGreen = clamp(baseGreen - 0.07f, 0.22f, 0.80f);
        float brightGreen = clamp(baseGreen + 0.05f, 0.22f, 0.85f);

        gl.glColor3f(0.10f, baseGreen, 0.14f);
        drawCone(gl, radius, height * 0.58f, lowerConeBaseY, 14);

        gl.glColor3f(0.12f, brightGreen, 0.16f);
        drawCone(gl, radius * 0.78f, height * 0.46f, upperConeBaseY, 14);

        gl.glColor3f(0.08f, darkGreen, 0.11f);
        drawCone(gl, radius * 0.62f, height * 0.34f, upperConeBaseY + height * 0.2f, 12);

        gl.glPopMatrix();
    }

    private void drawCylinder(GL2 gl, float trunkRadius, float trunkHeight, int segments) {
        gl.glBegin(GL2.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double t = (Math.PI * 2.0 * i) / segments;
            float nx = (float) Math.cos(t);
            float nz = (float) Math.sin(t);
            float px = nx * trunkRadius;
            float pz = nz * trunkRadius;

            gl.glNormal3f(nx, 0.0f, nz);
            gl.glVertex3f(px, 0.0f, pz);
            gl.glVertex3f(px, trunkHeight, pz);
        }
        gl.glEnd();

        gl.glBegin(GL2.GL_TRIANGLE_FAN);
        gl.glNormal3f(0.0f, 1.0f, 0.0f);
        gl.glVertex3f(0.0f, trunkHeight, 0.0f);
        for (int i = 0; i <= segments; i++) {
            double t = (Math.PI * 2.0 * (segments - i)) / segments;
            float px = (float) Math.cos(t) * trunkRadius;
            float pz = (float) Math.sin(t) * trunkRadius;
            gl.glVertex3f(px, trunkHeight, pz);
        }
        gl.glEnd();
    }

    private void drawCone(GL2 gl, float coneRadius, float coneHeight, float baseY, int segments) {
        float slope = coneRadius / Math.max(0.001f, coneHeight);

        gl.glBegin(GL2.GL_TRIANGLE_FAN);
        gl.glNormal3f(0.0f, 1.0f, 0.0f);
        gl.glVertex3f(0.0f, baseY + coneHeight, 0.0f);
        for (int i = 0; i <= segments; i++) {
            double t = (Math.PI * 2.0 * i) / segments;
            float nx = (float) Math.cos(t);
            float nz = (float) Math.sin(t);
            float px = nx * coneRadius;
            float pz = nz * coneRadius;
            float normalLength = (float) Math.sqrt(nx * nx + slope * slope + nz * nz);
            gl.glNormal3f(nx / normalLength, slope / normalLength, nz / normalLength);
            gl.glVertex3f(px, baseY, pz);
        }
        gl.glEnd();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public float getHeight() {
        return height;
    }

    public float getRadius() {
        return radius;
    }
}
