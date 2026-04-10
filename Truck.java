import java.util.List;

import com.jogamp.opengl.GL2;

public class Truck {

    private float x;
    private float y;
    private float z;
    private float speed;
    private float angle;
    private float tilt;
    private float pitch;
    private float wheelSpin;
    private float suspensionOffset;
    private float suspensionPhase;
    private float brakeLightIntensity;
    private float reverseLightIntensity;
    private boolean headlightsEnabled;
    private float indicatorTimer;
    private final GltfModel importedModel;

    private boolean forwardPressed;
    private boolean backwardPressed;
    private boolean leftPressed;
    private boolean rightPressed;

    private static final float UPDATE_DT = 1.0f / 60.0f;
    private static final float MAX_FORWARD_SPEED = 18.0f;
    private static final float MAX_REVERSE_SPEED = -7.0f;
    private static final float ACCELERATION = 24.0f;
    private static final float BRAKE_ACCELERATION = 36.0f;
    private static final float DRAG = 14.0f;
    private static final float TURN_RATE = 96.0f;
    private static final float WHEEL_RADIUS = 0.46f;
    private static final float WHEEL_CENTER_Y = -0.56f;
    private static final float WHEEL_HALF_WIDTH = 0.17f;
    private static final float GROUND_CLEARANCE = 0.015f;
    private static final float TRUCK_COLLISION_RADIUS = 1.05f;
    private static final float TREE_TRUNK_COLLISION_FACTOR = 0.30f;

    private static final float[][] WHEEL_OFFSETS = {
            { -1.05f, 1.25f },
            { 1.05f, 1.25f },
            { -1.05f, -0.35f },
            { 1.05f, -0.35f },
            { -1.05f, -1.45f },
            { 1.05f, -1.45f }
    };

    public Truck() {
        this.x = 50.0f;
        this.z = 50.0f;
        this.importedModel = new GltfModel("models/scene.gltf");
    }

    public void setPosition(float worldX, float worldZ) {
        this.x = worldX;
        this.z = worldZ;
    }

    public void setControls(boolean forward, boolean backward, boolean left, boolean right) {
        this.forwardPressed = forward;
        this.backwardPressed = backward;
        this.leftPressed = left;
        this.rightPressed = right;
    }

    public void update(HeightMap heightMap, List<Tree> trees, Road road) {
        updateSpeed();
        updateSteering();
        updatePosition(heightMap, trees);
        updateBodyAlignment(heightMap, road);
        updateSuspension();
        updateWheelAnimation();
        indicatorTimer += UPDATE_DT;
    }

    public void draw(GL2 gl) {
        gl.glPushMatrix();
        gl.glTranslatef(x, y + suspensionOffset, z);
        gl.glRotatef(angle, 0.0f, 1.0f, 0.0f);
        gl.glRotatef(pitch, 1.0f, 0.0f, 0.0f);
        gl.glRotatef(-tilt, 0.0f, 0.0f, 1.0f);

        if (importedModel.isLoaded()) {
            importedModel.draw(gl);
            drawFrontLightMarkers(gl);
            drawRearLights(gl);
        } else {
            drawChassis(gl);
            drawCabin(gl);
            drawWheels(gl);
        }

        gl.glPopMatrix();
    }

    private void drawChassis(GL2 gl) {
        gl.glColor3f(0.18f, 0.18f, 0.20f);
        drawCuboid(gl, -1.1f, -0.22f, -2.0f, 1.1f, 0.18f, 2.0f);

        gl.glColor3f(0.15f, 0.15f, 0.16f);
        drawCuboid(gl, -0.92f, -0.42f, -1.75f, 0.92f, -0.18f, 1.75f);

        drawRearLights(gl);
    }

    private void drawRearLights(GL2 gl) {
        boolean imported = importedModel.isLoaded();
        float lightScale = imported ? 2.0f : 1.0f;
        float tailPosIntensity = headlightsEnabled ? 0.36f : 0.0f;
        float brake = clamp(brakeLightIntensity, 0.0f, 1.0f);

        boolean blinkOn = ((int) (indicatorTimer * 2.8f)) % 2 == 0;
        // Steering is mapped oppositely in yaw update, so blinkers are swapped to match visual turn direction.
        float leftIndicator = rightPressed && !leftPressed && blinkOn ? 1.0f : 0.0f;
        float rightIndicator = leftPressed && !rightPressed && blinkOn ? 1.0f : 0.0f;

        float tailRed = 0.12f + 0.62f * tailPosIntensity;
        float tailGreenBlue = 0.01f + 0.02f * tailPosIntensity;
        float stopRed = 0.26f + 0.74f * brake;
        float stopGreenBlue = 0.02f + 0.02f * brake;

        float rearOuterX0 = -0.98f * lightScale;
        float rearOuterX1 = -0.58f * lightScale;
        float rearOuterX2 = 0.58f * lightScale;
        float rearOuterX3 = 0.98f * lightScale;

        float rearY0 = 0.04f * lightScale;
        float rearY1 = 0.28f * lightScale;
        float rearZ0 = -2.04f * lightScale;
        float rearZ1 = -1.96f * lightScale;

        float rearGlowX0 = -1.03f * lightScale;
        float rearGlowX1 = -0.53f * lightScale;
        float rearGlowX2 = 0.53f * lightScale;
        float rearGlowX3 = 1.03f * lightScale;
        float rearGlowY0 = -0.01f * lightScale;
        float rearGlowY1 = 0.34f * lightScale;
        float rearGlowZ0 = -2.08f * lightScale;
        float rearGlowZ1 = -1.90f * lightScale;

        float stopLeftX0 = -0.90f * lightScale;
        float stopLeftX1 = -0.66f * lightScale;
        float stopRightX0 = 0.66f * lightScale;
        float stopRightX1 = 0.90f * lightScale;
        float stopY0 = 0.08f * lightScale;
        float stopY1 = 0.24f * lightScale;
        float stopZ0 = -2.05f * lightScale;
        float stopZ1 = -1.95f * lightScale;

        float stopGlowLeftX0 = -0.94f * lightScale;
        float stopGlowLeftX1 = -0.62f * lightScale;
        float stopGlowRightX0 = 0.62f * lightScale;
        float stopGlowRightX1 = 0.94f * lightScale;
        float stopGlowY0 = 0.04f * lightScale;
        float stopGlowY1 = 0.28f * lightScale;
        float stopGlowZ0 = -2.10f * lightScale;
        float stopGlowZ1 = -1.88f * lightScale;

        float reverseX0 = -0.38f * lightScale;
        float reverseX1 = -0.15f * lightScale;
        float reverseX2 = 0.15f * lightScale;
        float reverseX3 = 0.38f * lightScale;
        float reverseY0 = 0.06f * lightScale;
        float reverseY1 = 0.22f * lightScale;
        float reverseZ0 = -2.04f * lightScale;
        float reverseZ1 = -1.96f * lightScale;

        float reverseGlowX0 = -0.43f * lightScale;
        float reverseGlowX1 = -0.10f * lightScale;
        float reverseGlowX2 = 0.10f * lightScale;
        float reverseGlowX3 = 0.43f * lightScale;
        float reverseGlowY0 = 0.02f * lightScale;
        float reverseGlowY1 = 0.28f * lightScale;
        float reverseGlowZ0 = -2.08f * lightScale;
        float reverseGlowZ1 = -1.90f * lightScale;

        if (imported) {
            rearOuterX0 = -1.12f;
            rearOuterX1 = -0.80f;
            rearOuterX2 = 0.80f;
            rearOuterX3 = 1.12f;
            rearY0 = -0.50f;
            rearY1 = -0.30f;
            rearZ0 = -4.23f;
            rearZ1 = -4.09f;

            rearGlowX0 = -1.16f;
            rearGlowX1 = -0.76f;
            rearGlowX2 = 0.76f;
            rearGlowX3 = 1.16f;
            rearGlowY0 = -0.56f;
            rearGlowY1 = -0.22f;
            rearGlowZ0 = -4.28f;
            rearGlowZ1 = -4.01f;

            stopLeftX0 = -1.08f;
            stopLeftX1 = -0.84f;
            stopRightX0 = 0.84f;
            stopRightX1 = 1.08f;
            stopY0 = -0.48f;
            stopY1 = -0.32f;
            stopZ0 = -4.22f;
            stopZ1 = -4.08f;

            stopGlowLeftX0 = -1.12f;
            stopGlowLeftX1 = -0.80f;
            stopGlowRightX0 = 0.80f;
            stopGlowRightX1 = 1.12f;
            stopGlowY0 = -0.54f;
            stopGlowY1 = -0.26f;
            stopGlowZ0 = -4.27f;
            stopGlowZ1 = -4.00f;

            reverseX0 = -0.48f;
            reverseX1 = -0.30f;
            reverseX2 = 0.30f;
            reverseX3 = 0.48f;
            reverseY0 = -0.50f;
            reverseY1 = -0.34f;
            reverseZ0 = -4.22f;
            reverseZ1 = -4.10f;

            reverseGlowX0 = -0.52f;
            reverseGlowX1 = -0.26f;
            reverseGlowX2 = 0.26f;
            reverseGlowX3 = 0.52f;
            reverseGlowY0 = -0.55f;
            reverseGlowY1 = -0.30f;
            reverseGlowZ0 = -4.27f;
            reverseGlowZ1 = -4.02f;
        }

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor3f(tailRed, tailGreenBlue, tailGreenBlue);
        drawCuboid(gl, rearOuterX0, rearY0, rearZ0, rearOuterX1, rearY1, rearZ1);
        drawCuboid(gl, rearOuterX2, rearY0, rearZ0, rearOuterX3, rearY1, rearZ1);

        if (tailPosIntensity > 0.01f) {
            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
            gl.glDepthMask(false);

            float glowAlpha = (imported ? 0.09f : 0.05f) + tailPosIntensity * (imported ? 0.26f : 0.18f);
            gl.glColor4f(1.0f, 0.10f, 0.08f, glowAlpha);
            drawCuboid(gl, rearGlowX0, rearGlowY0, rearGlowZ0, rearGlowX1, rearGlowY1, rearGlowZ1);
            drawCuboid(gl, rearGlowX2, rearGlowY0, rearGlowZ0, rearGlowX3, rearGlowY1, rearGlowZ1);

            gl.glDepthMask(true);
            gl.glDisable(GL2.GL_BLEND);
        }

        gl.glColor3f(stopRed, stopGreenBlue, stopGreenBlue);
        drawCuboid(gl, stopLeftX0, stopY0, stopZ0, stopLeftX1, stopY1, stopZ1);
        drawCuboid(gl, stopRightX0, stopY0, stopZ0, stopRightX1, stopY1, stopZ1);

        if (brake > 0.01f) {
            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
            gl.glDepthMask(false);

            float stopGlow = (imported ? 0.12f : 0.08f) + brake * (imported ? 0.30f : 0.22f);
            gl.glColor4f(1.0f, 0.08f, 0.06f, stopGlow);
            drawCuboid(gl, stopGlowLeftX0, stopGlowY0, stopGlowZ0, stopGlowLeftX1, stopGlowY1, stopGlowZ1);
            drawCuboid(gl, stopGlowRightX0, stopGlowY0, stopGlowZ0, stopGlowRightX1, stopGlowY1, stopGlowZ1);

            gl.glDepthMask(true);
            gl.glDisable(GL2.GL_BLEND);
        }

        float indicatorLeftBase = 0.14f + leftIndicator * 0.86f;
        float indicatorRightBase = 0.14f + rightIndicator * 0.86f;
        float indicatorY0 = imported ? -0.52f : 0.08f;
        float indicatorY1 = imported ? -0.34f : 0.20f;
        float indicatorZ0 = imported ? -4.25f : -2.08f;
        float indicatorZ1 = imported ? -4.10f : -1.94f;
        float indicatorLeftX0 = imported ? -1.22f : -1.02f;
        float indicatorLeftX1 = imported ? -1.12f : -0.94f;
        float indicatorRightX0 = imported ? 1.12f : 0.94f;
        float indicatorRightX1 = imported ? 1.22f : 1.02f;

        gl.glColor3f(indicatorLeftBase, 0.32f * indicatorLeftBase, 0.02f);
        drawCuboid(gl, indicatorLeftX0, indicatorY0, indicatorZ0, indicatorLeftX1, indicatorY1, indicatorZ1);
        gl.glColor3f(indicatorRightBase, 0.32f * indicatorRightBase, 0.02f);
        drawCuboid(gl, indicatorRightX0, indicatorY0, indicatorZ0, indicatorRightX1, indicatorY1, indicatorZ1);

        if (leftIndicator > 0.01f || rightIndicator > 0.01f) {
            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
            gl.glDepthMask(false);

            if (leftIndicator > 0.01f) {
                gl.glColor4f(1.0f, 0.42f, 0.05f, imported ? 0.26f : 0.18f);
                drawCuboid(gl, indicatorLeftX0 - 0.03f, indicatorY0 - 0.04f, indicatorZ0 - 0.06f,
                    indicatorLeftX1 + 0.03f, indicatorY1 + 0.05f, indicatorZ1 + 0.06f);
            }
            if (rightIndicator > 0.01f) {
                gl.glColor4f(1.0f, 0.42f, 0.05f, imported ? 0.26f : 0.18f);
                drawCuboid(gl, indicatorRightX0 - 0.03f, indicatorY0 - 0.04f, indicatorZ0 - 0.06f,
                    indicatorRightX1 + 0.03f, indicatorY1 + 0.05f, indicatorZ1 + 0.06f);
            }

            gl.glDepthMask(true);
            gl.glDisable(GL2.GL_BLEND);
        }

        float reverseLit = clamp(reverseLightIntensity, 0.0f, 1.0f);
        float reverseMain = 0.45f + reverseLit * 0.55f;
        float reverseTint = 0.46f + reverseLit * 0.52f;
        gl.glColor3f(reverseMain, reverseMain, reverseTint);
        drawCuboid(gl, reverseX0, reverseY0, reverseZ0, reverseX1, reverseY1, reverseZ1);
        drawCuboid(gl, reverseX2, reverseY0, reverseZ0, reverseX3, reverseY1, reverseZ1);

        if (reverseLit > 0.01f) {
            gl.glEnable(GL2.GL_BLEND);
            gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
            gl.glDepthMask(false);

            float reverseGlow = (imported ? 0.14f : 0.09f) + reverseLit * (imported ? 0.36f : 0.26f);
            gl.glColor4f(0.90f, 0.94f, 1.0f, reverseGlow);
            drawCuboid(gl, reverseGlowX0, reverseGlowY0, reverseGlowZ0, reverseGlowX1, reverseGlowY1, reverseGlowZ1);
            drawCuboid(gl, reverseGlowX2, reverseGlowY0, reverseGlowZ0, reverseGlowX3, reverseGlowY1, reverseGlowZ1);

            gl.glDepthMask(true);
            gl.glDisable(GL2.GL_BLEND);
        }
        gl.glEnable(GL2.GL_LIGHTING);
    }

    private void drawFrontLightMarkers(GL2 gl) {
        float headIntensity = headlightsEnabled ? 1.0f : 0.0f;
        if (headIntensity <= 0.0f) {
            return;
        }

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glColor3f(1.0f, 0.96f, 0.88f);
        drawCuboid(gl, -0.98f, -0.18f, 3.86f, -0.56f, 0.08f, 4.12f);
        drawCuboid(gl, 0.56f, -0.18f, 3.86f, 0.98f, 0.08f, 4.12f);

        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);
        gl.glColor4f(1.0f, 0.95f, 0.80f, 0.55f);
        drawCuboid(gl, -1.10f, -0.30f, 3.80f, -0.44f, 0.22f, 4.35f);
        drawCuboid(gl, 0.44f, -0.30f, 3.80f, 1.10f, 0.22f, 4.35f);
        gl.glDepthMask(true);
        gl.glDisable(GL2.GL_BLEND);
        gl.glEnable(GL2.GL_LIGHTING);
    }

    private void drawCabin(GL2 gl) {
        // Main red cabin mass.
        gl.glColor3f(0.78f, 0.08f, 0.08f);
        drawCuboid(gl, -1.00f, 0.18f, -0.70f, 1.00f, 1.42f, 1.38f);

        // Tall Scania-like front face.
        gl.glColor3f(0.82f, 0.11f, 0.11f);
        drawCuboid(gl, -0.97f, 0.50f, 1.36f, 0.97f, 1.33f, 2.00f);

        // Roof spoiler/visor block.
        gl.glColor3f(0.76f, 0.09f, 0.09f);
        drawCuboid(gl, -0.80f, 1.40f, 0.08f, 0.80f, 1.64f, 1.34f);

        // Lower center bumper support.
        gl.glColor3f(0.18f, 0.18f, 0.18f);
        drawCuboid(gl, -0.56f, 0.22f, -1.92f, 0.56f, 0.45f, -1.33f);

        // Side skirts and door shoulder, helps silhouette look more like modern Scania.
        gl.glColor3f(0.70f, 0.07f, 0.07f);
        drawCuboid(gl, -1.02f, 0.36f, -0.08f, -0.92f, 1.30f, 1.30f);
        drawCuboid(gl, 0.92f, 0.36f, -0.08f, 1.02f, 1.30f, 1.30f);

        gl.glColor3f(0.62f, 0.06f, 0.06f);
        drawCuboid(gl, -0.98f, 0.70f, 0.76f, 0.98f, 0.78f, 0.90f);

        drawScaniaFrontDetails(gl);
        drawFrontCornerPanels(gl);
        drawDoorAndSideDetails(gl);
        drawStepDetails(gl);
        drawMirrors(gl);
        drawRoofLamps(gl);
        drawCabinGlass(gl);
    }

    private void drawScaniaFrontDetails(GL2 gl) {
        // Dark front mask around grille and lights.
        gl.glColor3f(0.09f, 0.09f, 0.10f);
        drawCuboid(gl, -0.87f, 0.30f, 2.00f, 0.87f, 1.22f, 2.05f);

        // Metallic frame around the grille area.
        gl.glColor3f(0.46f, 0.46f, 0.48f);
        drawCuboid(gl, -0.91f, 0.28f, 1.98f, -0.86f, 1.24f, 2.06f);
        drawCuboid(gl, 0.86f, 0.28f, 1.98f, 0.91f, 1.24f, 2.06f);
        drawCuboid(gl, -0.91f, 1.20f, 1.98f, 0.91f, 1.25f, 2.06f);
        drawCuboid(gl, -0.89f, 0.25f, 1.98f, 0.89f, 0.30f, 2.06f);

        // Top logo shelf (where SCANIA text would be).
        gl.glColor3f(0.14f, 0.14f, 0.15f);
        drawCuboid(gl, -0.74f, 1.03f, 2.03f, 0.74f, 1.16f, 2.10f);

        // Bright logo strip.
        gl.glColor3f(0.86f, 0.86f, 0.86f);
        drawCuboid(gl, -0.40f, 1.07f, 2.08f, 0.40f, 1.12f, 2.12f);

        // Signature Scania stepped grille bars.
        float[] widths = { 0.74f, 0.70f, 0.66f, 0.62f, 0.58f, 0.54f };
        for (int i = 0; i < widths.length; i++) {
            float y0 = 0.36f + i * 0.11f;
            float y1 = y0 + 0.07f;
            float half = widths[i];

            gl.glColor3f(0.18f, 0.18f, 0.19f);
            drawCuboid(gl, -half, y0, 2.03f, half, y1, 2.08f);

            gl.glColor3f(0.30f, 0.30f, 0.31f);
            drawCuboid(gl, -(half - 0.04f), y0 + 0.01f, 2.08f, (half - 0.04f), y1 - 0.01f, 2.10f);
        }

        // Lower bumper modules.
        gl.glColor3f(0.12f, 0.12f, 0.13f);
        drawCuboid(gl, -0.88f, 0.18f, 1.98f, 0.88f, 0.32f, 2.07f);
        drawCuboid(gl, -0.78f, 0.08f, 1.97f, 0.78f, 0.18f, 2.05f);

        // Chrome trim slices in the bumper.
        gl.glColor3f(0.58f, 0.58f, 0.60f);
        drawCuboid(gl, -0.66f, 0.20f, 2.07f, 0.66f, 0.23f, 2.10f);
        drawCuboid(gl, -0.58f, 0.10f, 2.05f, 0.58f, 0.13f, 2.08f);

        // Fog-light housings.
        gl.glColor3f(0.90f, 0.92f, 0.96f);
        drawCuboid(gl, -0.70f, 0.12f, 2.02f, -0.48f, 0.19f, 2.08f);
        drawCuboid(gl, 0.48f, 0.12f, 2.02f, 0.70f, 0.19f, 2.08f);

        // Outer corner light modules.
        gl.glColor3f(0.92f, 0.70f, 0.26f);
        drawCuboid(gl, -0.90f, 0.34f, 2.01f, -0.84f, 0.46f, 2.07f);
        drawCuboid(gl, 0.84f, 0.34f, 2.01f, 0.90f, 0.46f, 2.07f);

        // Headlights.
        gl.glColor3f(0.93f, 0.95f, 0.98f);
        drawCuboid(gl, -0.87f, 0.28f, 1.98f, -0.48f, 0.46f, 2.05f);
        drawCuboid(gl, 0.48f, 0.28f, 1.98f, 0.87f, 0.46f, 2.05f);

        // Upper slim headlight modules.
        gl.glColor3f(0.88f, 0.90f, 0.94f);
        drawCuboid(gl, -0.83f, 0.95f, 1.99f, -0.60f, 1.03f, 2.05f);
        drawCuboid(gl, 0.60f, 0.95f, 1.99f, 0.83f, 1.03f, 2.05f);

        // Daytime running strips.
        gl.glColor3f(0.86f, 0.88f, 0.92f);
        drawCuboid(gl, -0.83f, 0.49f, 2.01f, -0.54f, 0.54f, 2.06f);
        drawCuboid(gl, 0.54f, 0.49f, 2.01f, 0.83f, 0.54f, 2.06f);

        // Small lower model badge block.
        gl.glColor3f(0.70f, 0.72f, 0.75f);
        drawCuboid(gl, -0.80f, 0.22f, 2.05f, -0.64f, 0.27f, 2.10f);

        // License plate area.
        gl.glColor3f(0.16f, 0.16f, 0.17f);
        drawCuboid(gl, -0.22f, 0.06f, 2.03f, 0.22f, 0.17f, 2.08f);

        // Black sun visor over windshield.
        gl.glColor3f(0.12f, 0.12f, 0.13f);
        drawCuboid(gl, -0.94f, 1.12f, 1.92f, 0.94f, 1.32f, 2.02f);
    }

    private void drawFrontCornerPanels(GL2 gl) {
        gl.glColor3f(0.68f, 0.08f, 0.08f);
        drawCuboid(gl, -1.00f, 0.24f, 1.22f, -0.88f, 1.24f, 1.92f);
        drawCuboid(gl, 0.88f, 0.24f, 1.22f, 1.00f, 1.24f, 1.92f);

        gl.glColor3f(0.22f, 0.22f, 0.23f);
        drawCuboid(gl, -1.01f, 0.22f, 1.00f, -0.95f, 0.52f, 1.24f);
        drawCuboid(gl, 0.95f, 0.22f, 1.00f, 1.01f, 0.52f, 1.24f);
    }

    private void drawDoorAndSideDetails(GL2 gl) {
        // Door seams.
        gl.glColor3f(0.18f, 0.02f, 0.02f);
        drawCuboid(gl, -0.99f, 0.44f, 0.30f, -0.96f, 1.28f, 0.34f);
        drawCuboid(gl, 0.96f, 0.44f, 0.30f, 0.99f, 1.28f, 0.34f);

        // Handles.
        gl.glColor3f(0.20f, 0.20f, 0.21f);
        drawCuboid(gl, -1.02f, 0.86f, 0.74f, -0.95f, 0.92f, 0.90f);
        drawCuboid(gl, 0.95f, 0.86f, 0.74f, 1.02f, 0.92f, 0.90f);

        // Side intake / trim panel behind front wheel arch region.
        gl.glColor3f(0.14f, 0.14f, 0.15f);
        drawCuboid(gl, -1.01f, 0.40f, 1.02f, -0.95f, 0.72f, 1.20f);
        drawCuboid(gl, 0.95f, 0.40f, 1.02f, 1.01f, 0.72f, 1.20f);
    }

    private void drawStepDetails(GL2 gl) {
        // Multi-step lower stair like on real tractors.
        gl.glColor3f(0.11f, 0.11f, 0.12f);
        drawCuboid(gl, -1.01f, 0.18f, 0.18f, -0.92f, 0.26f, 0.72f);
        drawCuboid(gl, -1.02f, 0.28f, 0.22f, -0.92f, 0.36f, 0.70f);
        drawCuboid(gl, -1.02f, 0.38f, 0.26f, -0.92f, 0.46f, 0.66f);

        drawCuboid(gl, 0.92f, 0.18f, 0.18f, 1.01f, 0.26f, 0.72f);
        drawCuboid(gl, 0.92f, 0.28f, 0.22f, 1.02f, 0.36f, 0.70f);
        drawCuboid(gl, 0.92f, 0.38f, 0.26f, 1.02f, 0.46f, 0.66f);

        // Metallic anti-slip edge.
        gl.glColor3f(0.42f, 0.42f, 0.44f);
        drawCuboid(gl, -1.01f, 0.46f, 0.26f, -0.92f, 0.48f, 0.66f);
        drawCuboid(gl, 0.92f, 0.46f, 0.26f, 1.01f, 0.48f, 0.66f);
    }

    private void drawMirrors(GL2 gl) {
        // Left mirror arm + housing.
        gl.glColor3f(0.14f, 0.14f, 0.15f);
        drawCuboid(gl, -1.10f, 0.88f, 1.08f, -0.99f, 0.95f, 1.76f);
        drawCuboid(gl, -1.30f, 0.76f, 1.14f, -1.10f, 1.05f, 1.54f);
        gl.glColor3f(0.10f, 0.10f, 0.11f);
        drawCuboid(gl, -1.34f, 0.80f, 1.18f, -1.30f, 1.02f, 1.50f);

        // Right mirror arm + housing.
        gl.glColor3f(0.14f, 0.14f, 0.15f);
        drawCuboid(gl, 0.99f, 0.88f, 1.08f, 1.10f, 0.95f, 1.76f);
        drawCuboid(gl, 1.10f, 0.76f, 1.14f, 1.30f, 1.05f, 1.54f);
        gl.glColor3f(0.10f, 0.10f, 0.11f);
        drawCuboid(gl, 1.30f, 0.80f, 1.18f, 1.34f, 1.02f, 1.50f);
    }

    private void drawRoofLamps(GL2 gl) {
        gl.glColor3f(0.13f, 0.13f, 0.14f);
        for (int i = -3; i <= 3; i++) {
            float xPos = i * 0.21f;
            drawCuboid(gl, xPos - 0.08f, 1.60f, 1.13f, xPos + 0.08f, 1.69f, 1.30f);
            gl.glColor3f(0.86f, 0.88f, 0.92f);
            drawCuboid(gl, xPos - 0.05f, 1.62f, 1.28f, xPos + 0.05f, 1.67f, 1.32f);
            gl.glColor3f(0.13f, 0.13f, 0.14f);
        }
    }

    private void drawCabinGlass(GL2 gl) {
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glDepthMask(false);

        // Windshield as a slanted quad.
        gl.glColor4f(0.20f, 0.32f, 0.42f, 0.34f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glNormal3f(0.0f, 0.30f, 1.0f);
        gl.glVertex3f(-0.78f, 0.70f, 1.90f);
        gl.glVertex3f(0.78f, 0.70f, 1.90f);
        gl.glVertex3f(0.74f, 1.14f, 1.58f);
        gl.glVertex3f(-0.74f, 1.14f, 1.58f);
        gl.glEnd();

        // Side windows.
        gl.glColor4f(0.19f, 0.30f, 0.40f, 0.32f);
        drawCuboid(gl, -0.98f, 0.78f, 0.82f, -0.90f, 1.18f, 1.30f);
        drawCuboid(gl, 0.90f, 0.78f, 0.82f, 0.98f, 1.18f, 1.30f);

        gl.glDepthMask(true);
        gl.glDisable(GL2.GL_BLEND);
    }

    private void drawWheels(GL2 gl) {
        float wheelRadius = WHEEL_RADIUS;
        float halfWidth = WHEEL_HALF_WIDTH;
        float wheelCenterY = WHEEL_CENTER_Y;

        gl.glColor3f(0.08f, 0.08f, 0.08f);
        drawWheel(gl, -1.05f, wheelCenterY, 1.25f, wheelRadius, halfWidth, 18, true);
        drawWheel(gl, 1.05f, wheelCenterY, 1.25f, wheelRadius, halfWidth, 18, true);

        drawWheel(gl, -1.05f, wheelCenterY, -0.35f, wheelRadius, halfWidth, 18, false);
        drawWheel(gl, 1.05f, wheelCenterY, -0.35f, wheelRadius, halfWidth, 18, false);
        drawWheel(gl, -1.05f, wheelCenterY, -1.45f, wheelRadius, halfWidth, 18, false);
        drawWheel(gl, 1.05f, wheelCenterY, -1.45f, wheelRadius, halfWidth, 18, false);
    }

    private void drawCuboid(GL2 gl, float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        gl.glBegin(GL2.GL_QUADS);

        gl.glNormal3f(0.0f, 0.0f, 1.0f);
        gl.glVertex3f(minX, minY, maxZ);
        gl.glVertex3f(maxX, minY, maxZ);
        gl.glVertex3f(maxX, maxY, maxZ);
        gl.glVertex3f(minX, maxY, maxZ);

        gl.glNormal3f(0.0f, 0.0f, -1.0f);
        gl.glVertex3f(maxX, minY, minZ);
        gl.glVertex3f(minX, minY, minZ);
        gl.glVertex3f(minX, maxY, minZ);
        gl.glVertex3f(maxX, maxY, minZ);

        gl.glNormal3f(-1.0f, 0.0f, 0.0f);
        gl.glVertex3f(minX, minY, minZ);
        gl.glVertex3f(minX, minY, maxZ);
        gl.glVertex3f(minX, maxY, maxZ);
        gl.glVertex3f(minX, maxY, minZ);

        gl.glNormal3f(1.0f, 0.0f, 0.0f);
        gl.glVertex3f(maxX, minY, maxZ);
        gl.glVertex3f(maxX, minY, minZ);
        gl.glVertex3f(maxX, maxY, minZ);
        gl.glVertex3f(maxX, maxY, maxZ);

        gl.glNormal3f(0.0f, 1.0f, 0.0f);
        gl.glVertex3f(minX, maxY, maxZ);
        gl.glVertex3f(maxX, maxY, maxZ);
        gl.glVertex3f(maxX, maxY, minZ);
        gl.glVertex3f(minX, maxY, minZ);

        gl.glNormal3f(0.0f, -1.0f, 0.0f);
        gl.glVertex3f(minX, minY, minZ);
        gl.glVertex3f(maxX, minY, minZ);
        gl.glVertex3f(maxX, minY, maxZ);
        gl.glVertex3f(minX, minY, maxZ);

        gl.glEnd();
    }

    private void drawWheel(GL2 gl, float centerX, float centerY, float centerZ, float radius, float halfWidth, int segments,
            boolean steered) {
        gl.glPushMatrix();
        gl.glTranslatef(centerX, centerY, centerZ);
        if (steered) {
            float steerVisual = clamp((leftPressed ? 1.0f : 0.0f) - (rightPressed ? 1.0f : 0.0f), -1.0f, 1.0f) * 18.0f;
            gl.glRotatef(steerVisual, 0.0f, 1.0f, 0.0f);
        }
        gl.glRotatef(wheelSpin, 1.0f, 0.0f, 0.0f);

        gl.glBegin(GL2.GL_QUAD_STRIP);
        for (int i = 0; i <= segments; i++) {
            double t = (Math.PI * 2.0 * i) / segments;
            float py = (float) Math.cos(t) * radius;
            float pz = (float) Math.sin(t) * radius;

            gl.glNormal3f(0.0f, py / radius, pz / radius);
            gl.glVertex3f(-halfWidth, py, pz);
            gl.glVertex3f(halfWidth, py, pz);
        }
        gl.glEnd();

        gl.glBegin(GL2.GL_TRIANGLE_FAN);
        gl.glNormal3f(-1.0f, 0.0f, 0.0f);
        gl.glVertex3f(-halfWidth, 0.0f, 0.0f);
        for (int i = 0; i <= segments; i++) {
            double t = (Math.PI * 2.0 * i) / segments;
            float py = (float) Math.cos(t) * radius;
            float pz = (float) Math.sin(t) * radius;
            gl.glVertex3f(-halfWidth, py, pz);
        }
        gl.glEnd();

        gl.glBegin(GL2.GL_TRIANGLE_FAN);
        gl.glNormal3f(1.0f, 0.0f, 0.0f);
        gl.glVertex3f(halfWidth, 0.0f, 0.0f);
        for (int i = 0; i <= segments; i++) {
            double t = (Math.PI * 2.0 * (segments - i)) / segments;
            float py = (float) Math.cos(t) * radius;
            float pz = (float) Math.sin(t) * radius;
            gl.glVertex3f(halfWidth, py, pz);
        }
        gl.glEnd();

        gl.glColor3f(0.62f, 0.62f, 0.62f);
        float hubRadius = radius * 0.35f;
        gl.glBegin(GL2.GL_TRIANGLE_FAN);
        gl.glNormal3f(1.0f, 0.0f, 0.0f);
        gl.glVertex3f(halfWidth + 0.01f, 0.0f, 0.0f);
        for (int i = 0; i <= segments; i++) {
            double t = (Math.PI * 2.0 * (segments - i)) / segments;
            float py = (float) Math.cos(t) * hubRadius;
            float pz = (float) Math.sin(t) * hubRadius;
            gl.glVertex3f(halfWidth + 0.01f, py, pz);
        }
        gl.glEnd();

        gl.glColor3f(0.08f, 0.08f, 0.08f);
        gl.glPopMatrix();
    }

    private void updateSpeed() {
        boolean brakingForward = backwardPressed && speed > 0.2f;

        if (forwardPressed) {
            speed += ACCELERATION * UPDATE_DT;
        }

        if (backwardPressed) {
            if (speed > 0.0f) {
                speed -= BRAKE_ACCELERATION * UPDATE_DT;
            } else {
                speed -= ACCELERATION * UPDATE_DT;
            }
        }

        if (!forwardPressed && !backwardPressed) {
            if (speed > 0.0f) {
                speed = Math.max(0.0f, speed - DRAG * UPDATE_DT);
            } else if (speed < 0.0f) {
                speed = Math.min(0.0f, speed + DRAG * UPDATE_DT);
            }
        }

        speed = clamp(speed, MAX_REVERSE_SPEED, MAX_FORWARD_SPEED);

        float brakeTarget = brakingForward ? 1.0f : 0.0f;
        brakeLightIntensity = approach(brakeLightIntensity, brakeTarget, 5.5f * UPDATE_DT);

        float reverseTarget = speed < -0.15f ? 1.0f : 0.0f;
        reverseLightIntensity = approach(reverseLightIntensity, reverseTarget, 4.6f * UPDATE_DT);
    }

    private void updateSuspension() {
        float speedAbs = Math.abs(speed);
        float roughness = clamp((Math.abs(pitch) + Math.abs(tilt)) / 20.0f, 0.0f, 1.0f);
        float amplitude = 0.0015f + speedAbs * 0.0004f + roughness * 0.0035f;
        amplitude = clamp(amplitude, 0.0015f, 0.010f);

        suspensionPhase += (2.1f + speedAbs * 0.22f + roughness * 2.0f) * UPDATE_DT;
        float targetOffset = (float) Math.sin(suspensionPhase) * amplitude;
        suspensionOffset = approach(suspensionOffset, targetOffset, 0.03f);
    }

    private void updateSteering() {
        float steerInput = 0.0f;
        if (leftPressed) {
            steerInput += 1.0f;
        }
        if (rightPressed) {
            steerInput -= 1.0f;
        }

        if (Math.abs(speed) > 0.05f && steerInput != 0.0f) {
            float speedFactor = Math.max(0.25f, Math.min(1.0f, Math.abs(speed) / MAX_FORWARD_SPEED));
            angle += steerInput * TURN_RATE * speedFactor * UPDATE_DT * (speed >= 0.0f ? 1.0f : -1.0f);
        }
    }

    private void updatePosition(HeightMap heightMap, List<Tree> trees) {
        float headingRadians = (float) Math.toRadians(angle);
        float dirX = (float) Math.sin(headingRadians);
        float dirZ = (float) Math.cos(headingRadians);

        float nextX = x + dirX * speed * UPDATE_DT;
        float nextZ = z + dirZ * speed * UPDATE_DT;

        float minBound = 2.0f;
        float maxBound = heightMap.getSize() - 3.0f;
        nextX = clamp(nextX, minBound, maxBound);
        nextZ = clamp(nextZ, minBound, maxBound);

        if (!collidesWithTrees(nextX, nextZ, trees)) {
            x = nextX;
            z = nextZ;
            return;
        }

        boolean moved = false;
        if (!collidesWithTrees(nextX, z, trees)) {
            x = nextX;
            moved = true;
        }

        if (!collidesWithTrees(x, nextZ, trees)) {
            z = nextZ;
            moved = true;
        }

        if (!moved) {
            speed *= 0.22f;
        }
    }

    private boolean collidesWithTrees(float candidateX, float candidateZ, List<Tree> trees) {
        for (int i = 0; i < trees.size(); i++) {
            Tree tree = trees.get(i);
            float dx = candidateX - tree.getX();
            float dz = candidateZ - tree.getZ();

            float treeRadius = tree.getRadius() * TREE_TRUNK_COLLISION_FACTOR;
            float collisionRadius = TRUCK_COLLISION_RADIUS + treeRadius;
            if (dx * dx + dz * dz < collisionRadius * collisionRadius) {
                return true;
            }
        }

        return false;
    }

    private void updateBodyAlignment(HeightMap heightMap, Road road) {
        float headingRadians = (float) Math.toRadians(angle);
        float forwardX = (float) Math.sin(headingRadians);
        float forwardZ = (float) Math.cos(headingRadians);
        float rightX = (float) Math.cos(headingRadians);
        float rightZ = (float) -Math.sin(headingRadians);

        float sampleDistance = 1.6f;

        float hCenter = getGroundHeight(heightMap, road, x, z);
        float[] terrainNormal = computeGroundNormal(heightMap, road, sampleDistance);
        float[] normalInTruckFrame = rotateY(terrainNormal, -headingRadians);

        float localX = clamp(normalInTruckFrame[0], -1.0f, 1.0f);
        float localY = clamp(normalInTruckFrame[1], -1.0f, 1.0f);
        float localZ = clamp(normalInTruckFrame[2], -1.0f, 1.0f);

        float targetTilt = (float) Math.toDegrees(Math.asin(localX));
        float targetPitch = (float) Math.toDegrees(Math.atan2(localZ, localY));

        pitch = approach(pitch, clamp(targetPitch, -13.0f, 13.0f), 18.0f * UPDATE_DT);
        tilt = approach(tilt, clamp(targetTilt, -12.0f, 12.0f), 16.0f * UPDATE_DT);

        float supportY = computeSupportBaseHeight(heightMap, road, forwardX, forwardZ, rightX, rightZ);
        float fallbackY = hCenter + 0.94f;
        float targetY = Math.max(supportY, fallbackY);

        if (y < targetY) {
            y = targetY;
        } else {
            y = approach(y, targetY, 20.0f * UPDATE_DT);
        }
    }

    private float getGroundHeight(HeightMap heightMap, Road road, float sampleX, float sampleZ) {
        float terrainHeight = heightMap.getHeight(sampleX, sampleZ);
        float roadHeight = road.getSurfaceHeightAt(heightMap, sampleX, sampleZ);
        if (!Float.isNaN(roadHeight)) {
            return Math.max(terrainHeight, roadHeight);
        }
        return terrainHeight;
    }

    private float[] computeGroundNormal(HeightMap heightMap, Road road, float sampleDistance) {
        float hL = getGroundHeight(heightMap, road, x - sampleDistance, z);
        float hR = getGroundHeight(heightMap, road, x + sampleDistance, z);
        float hD = getGroundHeight(heightMap, road, x, z - sampleDistance);
        float hU = getGroundHeight(heightMap, road, x, z + sampleDistance);

        float dYdX = (hR - hL) / (sampleDistance * 2.0f);
        float dYdZ = (hU - hD) / (sampleDistance * 2.0f);

        float nx = -dYdX;
        float ny = 1.0f;
        float nz = -dYdZ;

        float length = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (length < 0.0001f) {
            return new float[] { 0.0f, 1.0f, 0.0f };
        }

        return new float[] { nx / length, ny / length, nz / length };
    }

    private float[] rotateY(float[] vector, float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float xRot = vector[0] * cos + vector[2] * sin;
        float zRot = -vector[0] * sin + vector[2] * cos;
        return new float[] { xRot, vector[1], zRot };
    }

    private float computeSupportBaseHeight(HeightMap heightMap, Road road, float forwardX, float forwardZ, float rightX, float rightZ) {
        float minWheelLocalY = WHEEL_CENTER_Y - WHEEL_RADIUS;
        float requiredBaseY = -Float.MAX_VALUE;

        for (int i = 0; i < WHEEL_OFFSETS.length; i++) {
            float localX = WHEEL_OFFSETS[i][0];
            float localZ = WHEEL_OFFSETS[i][1];

            float wheelWorldX = x + rightX * localX + forwardX * localZ;
            float wheelWorldZ = z + rightZ * localX + forwardZ * localZ;
            float terrainY = getGroundHeight(heightMap, road, wheelWorldX, wheelWorldZ);

            float localGroundY = transformLocalY(localX, minWheelLocalY, localZ);
            float wheelRequiredY = terrainY + GROUND_CLEARANCE - localGroundY;
            requiredBaseY = Math.max(requiredBaseY, wheelRequiredY);
        }

        return requiredBaseY;
    }

    private float transformLocalY(float localX, float localY, float localZ) {
        float rollRadians = (float) Math.toRadians(-tilt);
        float pitchRadians = (float) Math.toRadians(pitch);

        float yAfterRoll = (float) (localX * Math.sin(rollRadians) + localY * Math.cos(rollRadians));

        float yAfterPitch = (float) (yAfterRoll * Math.cos(pitchRadians) - localZ * Math.sin(pitchRadians));
        return yAfterPitch;
    }

    private void updateWheelAnimation() {
        float wheelCircumference = (float) (Math.PI * 2.0f * WHEEL_RADIUS);
        wheelSpin += (speed * UPDATE_DT / wheelCircumference) * 360.0f;
        if (wheelSpin > 360.0f || wheelSpin < -360.0f) {
            wheelSpin %= 360.0f;
        }
    }

    private float approach(float current, float target, float step) {
        if (current < target) {
            return Math.min(current + step, target);
        }
        return Math.max(current - step, target);
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

    public float getSpeed() {
        return speed;
    }

    public float getAngle() {
        return angle;
    }

    public float getTilt() {
        return tilt;
    }

    public float getPitch() {
        return pitch;
    }

    public void setHeadlightsEnabled(boolean enabled) {
        this.headlightsEnabled = enabled;
    }

    public boolean areHeadlightsEnabled() {
        return headlightsEnabled;
    }

    public boolean isUsingImportedModel() {
        return importedModel.isLoaded();
    }

    public float[] localPointToWorld(float localX, float localY, float localZ) {
        float rollRadians = (float) Math.toRadians(-tilt);
        float pitchRadians = (float) Math.toRadians(pitch);
        float yawRadians = (float) Math.toRadians(angle);

        // Apply the same rotation order as in draw(): Z(roll), X(pitch), Y(yaw), then translation.
        float x1 = (float) (localX * Math.cos(rollRadians) - localY * Math.sin(rollRadians));
        float y1 = (float) (localX * Math.sin(rollRadians) + localY * Math.cos(rollRadians));
        float z1 = localZ;

        float x2 = x1;
        float y2 = (float) (y1 * Math.cos(pitchRadians) - z1 * Math.sin(pitchRadians));
        float z2 = (float) (y1 * Math.sin(pitchRadians) + z1 * Math.cos(pitchRadians));

        float x3 = (float) (x2 * Math.cos(yawRadians) + z2 * Math.sin(yawRadians));
        float y3 = y2;
        float z3 = (float) (-x2 * Math.sin(yawRadians) + z2 * Math.cos(yawRadians));

        return new float[] {
                x + x3,
                y + suspensionOffset + y3,
                z + z3
        };
    }

    public float[] localDirectionToWorld(float localX, float localY, float localZ) {
        float[] origin = localPointToWorld(0.0f, 0.0f, 0.0f);
        float[] point = localPointToWorld(localX, localY, localZ);
        return new float[] {
                point[0] - origin[0],
                point[1] - origin[1],
                point[2] - origin[2]
        };
    }
}
