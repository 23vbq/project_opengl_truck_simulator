import com.jogamp.opengl.GL2;

public class Tree {

    private final float x;
    private final float y;
    private final float z;
    private final float height;
    private final float radius;

    public Tree(float x, float y, float z, float height, float radius) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.height = height;
        this.radius = radius;
    }

    public void draw(GL2 gl) {
        // Stage 5 implementation placeholder.
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
