import com.jogamp.opengl.GL2;

public class Truck {

    private float x;
    private float y;
    private float z;
    private float speed;
    private float angle;
    private float tilt;

    public Truck() {
        this.x = 50.0f;
        this.z = 50.0f;
    }

    public void update(HeightMap heightMap) {
        this.y = heightMap.getHeight(x, z);
    }

    public void draw(GL2 gl) {
        // Stage 2 implementation placeholder.
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

    public float getSpeed() {
        return speed;
    }

    public float getAngle() {
        return angle;
    }

    public float getTilt() {
        return tilt;
    }
}
