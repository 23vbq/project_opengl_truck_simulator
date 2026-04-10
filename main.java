import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import javax.swing.JFrame;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.FPSAnimator;

public class main extends JFrame implements GLEventListener, KeyListener {

    private static final long serialVersionUID = 1L;

    private GL2 gl;
    private GLU glu;
    private World world;
    private GLCanvas canvas;

    private boolean forwardPressed;
    private boolean backwardPressed;
    private boolean leftPressed;
    private boolean rightPressed;
    private boolean cabinViewEnabled;

    private boolean fogEnabled;
    private boolean rainEnabled;
    private long cycleStartNanos;
    private float cyclePhaseOffset;
    private float currentCelestialPhase;
    private float currentHeadlightIntensity;
    private float currentNightFactor;
    private boolean headlightsEnabled;

    private static final float DAY_CYCLE_DURATION_SECONDS = 120.0f;
    private static final float[] KEY_TIMES = { 0.00f, 0.25f, 0.50f, 0.75f, 1.00f };

    public main(String title) {
        super(title);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension screenSize = toolkit.getScreenSize();
        setBounds(screenSize.width / 6, screenSize.height / 6, (screenSize.width * 2) / 3, (screenSize.height * 2) / 3);

        GLProfile profile = GLProfile.get(GLProfile.GL2);
        GLCapabilities capabilities = new GLCapabilities(profile);
        canvas = new GLCanvas(capabilities);
        canvas.addGLEventListener(this);
        canvas.addKeyListener(this);
        canvas.setFocusable(true);

        add(canvas);
        setVisible(true);
        canvas.requestFocusInWindow();

        FPSAnimator animator = new FPSAnimator(canvas, 60, true);
        animator.start();
    }

    @Override
    public void init(GLAutoDrawable drawable) {
        gl = drawable.getGL().getGL2();
        glu = new GLU();
        world = new World();
        fogEnabled = true;
        rainEnabled = false;
        cabinViewEnabled = false;
        headlightsEnabled = false;
        cycleStartNanos = System.nanoTime();
        cyclePhaseOffset = 0.0f;

        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDisable(GL2.GL_CULL_FACE);

        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_LIGHT1);
        gl.glEnable(GL2.GL_NORMALIZE);
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE);

        gl.glFogi(GL2.GL_FOG_MODE, GL2.GL_LINEAR);
        gl.glHint(GL2.GL_FOG_HINT, GL2.GL_NICEST);
        gl.glFogf(GL2.GL_FOG_START, 20.0f);
        gl.glFogf(GL2.GL_FOG_END, 120.0f);
        gl.glEnable(GL2.GL_FOG);

        float[] ambient = { 0.35f, 0.35f, 0.35f, 1.0f };
        float[] diffuse = { 0.9f, 0.9f, 0.9f, 1.0f };
        float[] lightPosition = { 120.0f, 180.0f, 120.0f, 1.0f };
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, ambient, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPosition, 0);

        float[] headAmbient = { 0.0f, 0.0f, 0.0f, 1.0f };
        float[] headDiffuse = { 0.0f, 0.0f, 0.0f, 1.0f };
        float[] headSpecular = { 0.0f, 0.0f, 0.0f, 1.0f };
        float[] headPos = { 0.0f, 0.0f, 0.0f, 1.0f };
        float[] headDir = { 0.0f, -0.18f, 1.0f };
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_AMBIENT, headAmbient, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, headDiffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPECULAR, headSpecular, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, headPos, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPOT_DIRECTION, headDir, 0);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_CUTOFF, 28.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_EXPONENT, 18.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_CONSTANT_ATTENUATION, 1.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_LINEAR_ATTENUATION, 0.055f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_QUADRATIC_ATTENUATION, 0.008f);

        String renderer = gl.glGetString(GL2.GL_RENDERER);
        setTitle("Scania Driver | " + renderer);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        applyEnvironmentPreset();
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();

        world.setControls(forwardPressed, backwardPressed, leftPressed, rightPressed);
        world.setRainEnabled(rainEnabled);
        world.update();

        float elapsedSeconds = (System.nanoTime() - cycleStartNanos) / 1_000_000_000.0f;
        currentCelestialPhase = (elapsedSeconds / DAY_CYCLE_DURATION_SECONDS + cyclePhaseOffset) % 1.0f;

        Truck truck = world.getTruck();
        truck.setHeadlightsEnabled(headlightsEnabled);
        float headingRadians = (float) Math.toRadians(truck.getAngle());
        float headingX = (float) Math.sin(headingRadians);
        float headingZ = (float) Math.cos(headingRadians);
        float rightX = (float) Math.cos(headingRadians);
        float rightZ = (float) -Math.sin(headingRadians);

        float cameraX;
        float cameraY;
        float cameraZ;
        float targetX;
        float targetY;
        float targetZ;

        if (cabinViewEnabled) {
            float seatLocalX = truck.isUsingImportedModel() ? -0.95f : 0.0f;
            float seatLocalY = truck.isUsingImportedModel() ? 1.60f : 1.30f;
            float seatLocalZ = truck.isUsingImportedModel() ? 2.45f : 1.64f;
            float lookDistance = truck.isUsingImportedModel() ? 34.0f : 22.0f;

            float[] seatPos = truck.localPointToWorld(seatLocalX, seatLocalY, seatLocalZ);
            float[] forwardDir = truck.localDirectionToWorld(0.0f, -0.02f, 1.0f);
            float dirLen = (float) Math.sqrt(forwardDir[0] * forwardDir[0] + forwardDir[1] * forwardDir[1]
                    + forwardDir[2] * forwardDir[2]);
            if (dirLen < 0.0001f) {
                forwardDir[0] = headingX;
                forwardDir[1] = 0.0f;
                forwardDir[2] = headingZ;
                dirLen = 1.0f;
            }
            forwardDir[0] /= dirLen;
            forwardDir[1] /= dirLen;
            forwardDir[2] /= dirLen;

            cameraX = seatPos[0];
            cameraY = seatPos[1];
            cameraZ = seatPos[2];

            targetX = cameraX + forwardDir[0] * lookDistance;
            targetY = cameraY + forwardDir[1] * lookDistance;
            targetZ = cameraZ + forwardDir[2] * lookDistance;

            float speedAbs = Math.abs(truck.getSpeed());
            float shakeAmount = Math.min(0.025f, speedAbs * 0.0013f);
            float shakeTime = elapsedSeconds * (6.0f + speedAbs * 0.35f);
            float lateralShake = (float) Math.sin(shakeTime * 1.7f) * shakeAmount;
            float verticalShake = (float) Math.sin(shakeTime * 2.6f + 0.8f) * shakeAmount * 0.5f;

            cameraX += rightX * lateralShake;
            cameraY += verticalShake;
            cameraZ += rightZ * lateralShake;
            targetX += rightX * lateralShake;
            targetY += verticalShake * 0.8f;
            targetZ += rightZ * lateralShake;
        } else {
            float cameraDistance = 11.0f;
            float cameraHeight = 5.0f;

            cameraX = truck.getX() - headingX * cameraDistance;
            cameraY = truck.getY() + cameraHeight;
            cameraZ = truck.getZ() - headingZ * cameraDistance;

            targetX = truck.getX() + headingX * 2.2f;
            targetY = truck.getY() + 1.05f;
            targetZ = truck.getZ() + headingZ * 2.2f;
        }

        glu.gluLookAt(cameraX, cameraY, cameraZ, targetX, targetY, targetZ, 0.0f, 1.0f, 0.0f);

        applyTruckHeadlights(truck, headingX, headingZ);

        world.draw(gl);
        world.drawCelestialBodies(gl, currentCelestialPhase);

        gl.glFlush();
    }

    @Override
    public void reshape(GLAutoDrawable drawable, int x, int y, int width, int height) {
        if (height <= 0) {
            height = 1;
        }

        float aspect = (float) width / (float) height;
        gl.glViewport(0, 0, width, height);

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glLoadIdentity();
        glu.gluPerspective(55.0f, aspect, 0.1f, 600.0f);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        // Nothing to dispose at this stage.
    }

    @Override
    public void keyTyped(KeyEvent e) {
        // No action needed.
    }

    @Override
    public void keyPressed(KeyEvent e) {
        updateKeyState(e.getKeyCode(), true);
    }

    @Override
    public void keyReleased(KeyEvent e) {
        updateKeyState(e.getKeyCode(), false);
    }

    private void updateKeyState(int keyCode, boolean pressed) {
        if (keyCode == KeyEvent.VK_UP || keyCode == KeyEvent.VK_W) {
            forwardPressed = pressed;
        } else if (keyCode == KeyEvent.VK_DOWN || keyCode == KeyEvent.VK_S) {
            backwardPressed = pressed;
        } else if (keyCode == KeyEvent.VK_LEFT || keyCode == KeyEvent.VK_A) {
            leftPressed = pressed;
        } else if (keyCode == KeyEvent.VK_RIGHT || keyCode == KeyEvent.VK_D) {
            rightPressed = pressed;
        } else if (keyCode == KeyEvent.VK_T && pressed) {
            cyclePhaseOffset = (cyclePhaseOffset + 0.25f) % 1.0f;
        } else if (keyCode == KeyEvent.VK_F && pressed) {
            fogEnabled = !fogEnabled;
        } else if (keyCode == KeyEvent.VK_R && pressed) {
            rainEnabled = !rainEnabled;
        } else if (keyCode == KeyEvent.VK_C && pressed) {
            cabinViewEnabled = !cabinViewEnabled;
        } else if (keyCode == KeyEvent.VK_L && pressed) {
            headlightsEnabled = !headlightsEnabled;
        }
    }

    private void applyEnvironmentPreset() {
        float phase = currentCelestialPhase;

        float[][] clearColorKeys = {
                { 0.72f, 0.64f, 0.53f, 1.0f },
                { 0.58f, 0.79f, 0.98f, 1.0f },
                { 0.93f, 0.58f, 0.35f, 1.0f },
            { 0.02f, 0.03f, 0.06f, 1.0f },
                { 0.72f, 0.64f, 0.53f, 1.0f }
        };

        float[][] fogColorKeys = {
                { 0.70f, 0.60f, 0.50f, 1.0f },
                { 0.65f, 0.83f, 0.98f, 1.0f },
                { 0.86f, 0.52f, 0.35f, 1.0f },
            { 0.012f, 0.015f, 0.028f, 1.0f },
                { 0.70f, 0.60f, 0.50f, 1.0f }
        };

        float[][] ambientKeys = {
                { 0.28f, 0.26f, 0.24f, 1.0f },
                { 0.35f, 0.35f, 0.35f, 1.0f },
                { 0.42f, 0.31f, 0.25f, 1.0f },
            { 0.014f, 0.014f, 0.022f, 1.0f },
                { 0.28f, 0.26f, 0.24f, 1.0f }
        };

        float[][] diffuseKeys = {
                { 0.58f, 0.52f, 0.46f, 1.0f },
                { 0.90f, 0.90f, 0.90f, 1.0f },
                { 0.92f, 0.62f, 0.43f, 1.0f },
            { 0.045f, 0.050f, 0.078f, 1.0f },
                { 0.58f, 0.52f, 0.46f, 1.0f }
        };

        float[][] lightPositionKeys = {
                { -170.0f, 55.0f, 20.0f, 1.0f },
                { 120.0f, 180.0f, 120.0f, 1.0f },
                { -140.0f, 75.0f, -65.0f, 1.0f },
                { 45.0f, 130.0f, -105.0f, 1.0f },
                { -170.0f, 55.0f, 20.0f, 1.0f }
        };

        float[] fogStartKeys = { 16.0f, 24.0f, 15.0f, 8.0f, 16.0f };
        float[] fogEndKeys = { 95.0f, 130.0f, 90.0f, 50.0f, 95.0f };
        float[] clearColor = sampleCycleColor(phase, clearColorKeys);
        float[] fogColor = sampleCycleColor(phase, fogColorKeys);
        float[] ambient = sampleCycleColor(phase, ambientKeys);
        float[] diffuse = sampleCycleColor(phase, diffuseKeys);
        float[] lightPosition = sampleCycleColor(phase, lightPositionKeys);
        float fogStart = sampleCycleScalar(phase, fogStartKeys);
        float fogEnd = sampleCycleScalar(phase, fogEndKeys);

        float nightCenter = 0.75f;
        float nightWidth = 0.23f;
        float nightDistance = Math.abs(phase - nightCenter);
        currentNightFactor = clamp01(1.0f - nightDistance / nightWidth);
        currentHeadlightIntensity = headlightsEnabled ? (0.85f + currentNightFactor * 2.05f) : 0.0f;

        gl.glClearColor(clearColor[0], clearColor[1], clearColor[2], clearColor[3]);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, ambient, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPosition, 0);

        if (fogEnabled) {
            gl.glEnable(GL2.GL_FOG);
            gl.glFogf(GL2.GL_FOG_START, fogStart);
            gl.glFogf(GL2.GL_FOG_END, fogEnd);
            gl.glFogfv(GL2.GL_FOG_COLOR, fogColor, 0);
        } else {
            gl.glDisable(GL2.GL_FOG);
        }
    }

        private void applyTruckHeadlights(Truck truck, float headingX, float headingZ) {
        float intensity = Math.max(0.0f, Math.min(3.0f, currentHeadlightIntensity));

        float[] diffuse = {
            1.35f * intensity,
            1.28f * intensity,
            1.12f * intensity,
            1.0f
        };
        float[] specular = {
            0.95f * intensity,
            0.90f * intensity,
            0.78f * intensity,
            1.0f
        };

        float[] pos = {
            truck.getX() + headingX * 1.62f,
            truck.getY() + 0.78f,
            truck.getZ() + headingZ * 1.62f,
            1.0f
        };
        float[] dir = {
            headingX,
            -0.18f,
            headingZ
        };

        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_DIFFUSE, diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPECULAR, specular, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_POSITION, pos, 0);
        gl.glLightfv(GL2.GL_LIGHT1, GL2.GL_SPOT_DIRECTION, dir, 0);
        }

    private float[] sampleCycleColor(float phase, float[][] keys) {
        int index = 0;
        while (index < KEY_TIMES.length - 2 && phase > KEY_TIMES[index + 1]) {
            index++;
        }

        float segmentStart = KEY_TIMES[index];
        float segmentEnd = KEY_TIMES[index + 1];
        float t = (phase - segmentStart) / Math.max(0.0001f, segmentEnd - segmentStart);

        float[] a = keys[index];
        float[] b = keys[index + 1];
        return new float[] {
                lerp(a[0], b[0], t),
                lerp(a[1], b[1], t),
                lerp(a[2], b[2], t),
                lerp(a[3], b[3], t)
        };
    }

    private float sampleCycleScalar(float phase, float[] keys) {
        int index = 0;
        while (index < KEY_TIMES.length - 2 && phase > KEY_TIMES[index + 1]) {
            index++;
        }

        float segmentStart = KEY_TIMES[index];
        float segmentEnd = KEY_TIMES[index + 1];
        float t = (phase - segmentStart) / Math.max(0.0001f, segmentEnd - segmentStart);
        return lerp(keys[index], keys[index + 1], t);
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    public static void main(String[] args) {
        EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new main("Scania Driver");
            }
        });
    }
}
