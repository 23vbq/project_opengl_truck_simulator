import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

import javax.swing.JFrame;

import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.GLProfile;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.FPSAnimator;
import com.jogamp.opengl.util.awt.TextRenderer;

public class main extends JFrame implements GLEventListener, KeyListener, MouseListener, MouseMotionListener {

    private static final long serialVersionUID = 1L;

    private GL2 gl;
    private GLU glu;
    private World world;
    private AudioEngine audioEngine;
    private GLCanvas canvas;
    private TextRenderer textRenderer;
    private TextRenderer largeTextRenderer;

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
    private boolean audioEnabled;
    private boolean leftMouseDragging;
    private int lastMouseX;
    private int lastMouseY;
    private float thirdPersonOrbitYawDeg;
    private float thirdPersonOrbitPitchDeg;
    private float firstPersonLookYawDeg;
    private float firstPersonLookPitchDeg;

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
        canvas.addMouseListener(this);
        canvas.addMouseMotionListener(this);
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
        audioEnabled = true;
        cycleStartNanos = System.nanoTime();
        cyclePhaseOffset = 0.0f;
        thirdPersonOrbitYawDeg = 0.0f;
        thirdPersonOrbitPitchDeg = 0.0f;
        firstPersonLookYawDeg = 0.0f;
        firstPersonLookPitchDeg = 0.0f;

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
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_CUTOFF, 36.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_SPOT_EXPONENT, 9.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_CONSTANT_ATTENUATION, 1.0f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_LINEAR_ATTENUATION, 0.028f);
        gl.glLightf(GL2.GL_LIGHT1, GL2.GL_QUADRATIC_ATTENUATION, 0.0035f);

        textRenderer = new TextRenderer(new Font("Monospaced", Font.PLAIN, 14), true, true);
        largeTextRenderer = new TextRenderer(new Font("Monospaced", Font.BOLD, 24), true, true);

        audioEngine = new AudioEngine();
        audioEngine.setEnabled(audioEnabled);
        audioEngine.start();

        String renderer = gl.glGetString(GL2.GL_RENDERER);
        setTitle("Scania V8 Simulator - ETS3 in 1 night | " + renderer);
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
        world.setSkyPhase(currentCelestialPhase);

        Truck truck = world.getTruck();
        truck.setHeadlightsEnabled(headlightsEnabled);
        if (audioEngine != null) {
            audioEngine.update(truck.getSpeed(), truck.getEstimatedRpm(), truck.isForwardPressed(), truck.getCurrentGear() < 0,
                world.isRainEnabled(), truck.isIndicatorRequested(), truck.isIndicatorBlinkOn(),
                truck.getBrakeLightIntensity());
        }
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
        float upX = 0.0f;
        float upY = 1.0f;
        float upZ = 0.0f;

        if (cabinViewEnabled) {
            float seatLocalX = truck.isUsingImportedModel() ? 0.7f : 0.0f;
            float seatLocalY = truck.isUsingImportedModel() ? 1.82f : 1.30f;
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

            float[] cameraUp = normalizeVector(truck.localDirectionToWorld(0.0f, 1.0f, 0.0f));
            if (vectorLength(cameraUp) < 0.0001f) {
                cameraUp = new float[] { 0.0f, 1.0f, 0.0f };
            }

            forwardDir = rotateAroundAxis(forwardDir, cameraUp, (float) Math.toRadians(firstPersonLookYawDeg));
            float[] cameraRight = normalizeVector(cross(cameraUp, forwardDir));
            if (vectorLength(cameraRight) < 0.0001f) {
                cameraRight = new float[] { rightX, 0.0f, rightZ };
            }
            forwardDir = rotateAroundAxis(forwardDir, cameraRight, (float) Math.toRadians(firstPersonLookPitchDeg));
            forwardDir = normalizeVector(forwardDir);

            cameraRight = normalizeVector(cross(cameraUp, forwardDir));
            if (vectorLength(cameraRight) < 0.0001f) {
                cameraRight = new float[] { rightX, 0.0f, rightZ };
            }
            float[] finalUp = normalizeVector(cross(forwardDir, cameraRight));
            if (vectorLength(finalUp) >= 0.0001f) {
                upX = finalUp[0];
                upY = finalUp[1];
                upZ = finalUp[2];
            }

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
            float orbitYawDeg = truck.getAngle() + 180.0f + thirdPersonOrbitYawDeg;
            float orbitPitchDeg = clamp(-5.0f, 65.0f, 22.0f + thirdPersonOrbitPitchDeg);

            float orbitYawRad = (float) Math.toRadians(orbitYawDeg);
            float orbitPitchRad = (float) Math.toRadians(orbitPitchDeg);
            float horizontalDistance = (float) Math.cos(orbitPitchRad) * cameraDistance;

            cameraX = truck.getX() + (float) Math.sin(orbitYawRad) * horizontalDistance;
            cameraY = truck.getY() + 1.20f + (float) Math.sin(orbitPitchRad) * cameraDistance;
            cameraZ = truck.getZ() + (float) Math.cos(orbitYawRad) * horizontalDistance;

            targetX = truck.getX() + headingX * 0.8f;
            targetY = truck.getY() + 1.10f;
            targetZ = truck.getZ() + headingZ * 0.8f;
        }

        glu.gluLookAt(cameraX, cameraY, cameraZ, targetX, targetY, targetZ, upX, upY, upZ);

        applyTruckHeadlights(truck, headingX, headingZ);

        world.draw(gl);
        world.drawCelestialBodies(gl, currentCelestialPhase);

        // capture modelview/projection BEFORE switching to ortho, for lens flare gluProject
        double[] mvMatrix = new double[16];
        double[] projMatrix = new double[16];
        int[] viewport = new int[4];
        gl.glGetDoublev(GL2.GL_MODELVIEW_MATRIX, mvMatrix, 0);
        gl.glGetDoublev(GL2.GL_PROJECTION_MATRIX, projMatrix, 0);
        gl.glGetIntegerv(GL2.GL_VIEWPORT, viewport, 0);

        drawControlsUI(drawable, truck);
        drawLensFlare(drawable, mvMatrix, projMatrix, viewport);
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

    private void drawControlsUI(GLAutoDrawable drawable, Truck truck) {
        int width = drawable.getSurfaceWidth();
        int height = drawable.getSurfaceHeight();

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glOrtho(0.0, width, 0.0, height, -1.0, 1.0);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);

        drawScreenVignette(width, height);
        drawSpeedEffect(width, height, truck.getSpeedKmh());

        float controlPanelX = 12.0f;
        float controlPanelY = (float) height - 240.0f - 12.0f;
        float controlPanelW = 320.0f;
        float controlPanelH = 240.0f;

        float drivePanelX = 12.0f;
        float drivePanelY = 12.0f;
        float drivePanelW = 372.0f;
        float drivePanelH = 132.0f;

        float rpm = truck.getEstimatedRpm();
        float rpmMax = 2600.0f;
        float rpmRatio = clamp01(rpm / rpmMax);
        float speedKmh = truck.getSpeedKmh();
        float speedMax = 78.0f;
        float speedRatio = clamp01(speedKmh / speedMax);

        gl.glColor4f(0.04f, 0.04f, 0.06f, 0.78f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex2f(controlPanelX, controlPanelY);
        gl.glVertex2f(controlPanelX + controlPanelW, controlPanelY);
        gl.glVertex2f(controlPanelX + controlPanelW, controlPanelY + controlPanelH);
        gl.glVertex2f(controlPanelX, controlPanelY + controlPanelH);
        gl.glEnd();

        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex2f(drivePanelX, drivePanelY);
        gl.glVertex2f(drivePanelX + drivePanelW, drivePanelY);
        gl.glVertex2f(drivePanelX + drivePanelW, drivePanelY + drivePanelH);
        gl.glVertex2f(drivePanelX, drivePanelY + drivePanelH);
        gl.glEnd();

        gl.glColor4f(0.35f, 0.35f, 0.38f, 0.5f);
        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glVertex2f(controlPanelX, controlPanelY);
        gl.glVertex2f(controlPanelX + controlPanelW, controlPanelY);
        gl.glVertex2f(controlPanelX + controlPanelW, controlPanelY + controlPanelH);
        gl.glVertex2f(controlPanelX, controlPanelY + controlPanelH);
        gl.glEnd();

        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glVertex2f(drivePanelX, drivePanelY);
        gl.glVertex2f(drivePanelX + drivePanelW, drivePanelY);
        gl.glVertex2f(drivePanelX + drivePanelW, drivePanelY + drivePanelH);
        gl.glVertex2f(drivePanelX, drivePanelY + drivePanelH);
        gl.glEnd();

        float tachX = drivePanelX + 18.0f;
        float tachY = drivePanelY + 76.0f;
        float tachW = 212.0f;
        float tachH = 22.0f;

        gl.glColor4f(0.09f, 0.11f, 0.14f, 0.92f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex2f(tachX, tachY);
        gl.glVertex2f(tachX + tachW, tachY);
        gl.glVertex2f(tachX + tachW, tachY + tachH);
        gl.glVertex2f(tachX, tachY + tachH);
        gl.glEnd();

        float redZoneX = tachX + tachW * 0.84f;
        gl.glColor4f(0.46f, 0.07f, 0.08f, 0.88f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex2f(redZoneX, tachY);
        gl.glVertex2f(tachX + tachW, tachY);
        gl.glVertex2f(tachX + tachW, tachY + tachH);
        gl.glVertex2f(redZoneX, tachY + tachH);
        gl.glEnd();

        float fillW = tachW * rpmRatio;
        gl.glColor4f(0.86f, 0.88f, 0.92f, 0.96f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex2f(tachX, tachY + 1.5f);
        gl.glVertex2f(tachX + fillW, tachY + 1.5f);
        gl.glVertex2f(tachX + fillW, tachY + tachH - 1.5f);
        gl.glVertex2f(tachX, tachY + tachH - 1.5f);
        gl.glEnd();

        gl.glColor4f(0.40f, 0.43f, 0.48f, 0.85f);
        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glVertex2f(tachX, tachY);
        gl.glVertex2f(tachX + tachW, tachY);
        gl.glVertex2f(tachX + tachW, tachY + tachH);
        gl.glVertex2f(tachX, tachY + tachH);
        gl.glEnd();

        float speedX = drivePanelX + 18.0f;
        float speedY = drivePanelY + 34.0f;
        float speedW = 212.0f;
        float speedH = 22.0f;

        gl.glColor4f(0.09f, 0.11f, 0.14f, 0.92f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex2f(speedX, speedY);
        gl.glVertex2f(speedX + speedW, speedY);
        gl.glVertex2f(speedX + speedW, speedY + speedH);
        gl.glVertex2f(speedX, speedY + speedH);
        gl.glEnd();

        float speedFillW = speedW * speedRatio;
        gl.glColor4f(0.66f, 0.84f, 0.95f, 0.94f);
        gl.glBegin(GL2.GL_QUADS);
        gl.glVertex2f(speedX, speedY + 1.5f);
        gl.glVertex2f(speedX + speedFillW, speedY + 1.5f);
        gl.glVertex2f(speedX + speedFillW, speedY + speedH - 1.5f);
        gl.glVertex2f(speedX, speedY + speedH - 1.5f);
        gl.glEnd();

        gl.glColor4f(0.40f, 0.43f, 0.48f, 0.85f);
        gl.glBegin(GL2.GL_LINE_LOOP);
        gl.glVertex2f(speedX, speedY);
        gl.glVertex2f(speedX + speedW, speedY);
        gl.glVertex2f(speedX + speedW, speedY + speedH);
        gl.glVertex2f(speedX, speedY + speedH);
        gl.glEnd();

        gl.glDisable(GL2.GL_BLEND);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_LIGHTING);

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPopMatrix();

        textRenderer.setColor(0.85f, 0.88f, 0.92f, 0.95f);
        textRenderer.beginRendering(width, height);
        textRenderer.draw("SCANIA DRIVER", (int) controlPanelX + 12, (int) controlPanelY + 20);
        textRenderer.setColor(0.60f, 0.68f, 0.78f, 0.90f);
        textRenderer.draw("RPM", (int) tachX, (int) tachY + 24);
        textRenderer.draw((int) rpm + " / 2600", (int) tachX + 112, (int) tachY + 24);
        textRenderer.draw("SPEED", (int) speedX, (int) speedY + 24);
        textRenderer.draw((int) speedKmh + " km/h", (int) speedX + 112, (int) speedY + 24);

        textRenderer.draw("W/UP - Throttle", (int) controlPanelX + 12, (int) controlPanelY + 44);
        textRenderer.draw("S/DN - Brake", (int) controlPanelX + 12, (int) controlPanelY + 62);
        textRenderer.draw("Shift - Gear Up", (int) controlPanelX + 12, (int) controlPanelY + 80);
        textRenderer.draw("Ctrl - Gear Down", (int) controlPanelX + 12, (int) controlPanelY + 98);
        textRenderer.draw("A/LF - Turn Left", (int) controlPanelX + 12, (int) controlPanelY + 116);
        textRenderer.draw("D/RT - Turn Right", (int) controlPanelX + 12, (int) controlPanelY + 134);
        textRenderer.draw("C - Cabin View", (int) controlPanelX + 12, (int) controlPanelY + 152);
        textRenderer.draw("L - Lights", (int) controlPanelX + 12, (int) controlPanelY + 170);
        textRenderer.draw("F - Fog", (int) controlPanelX + 12, (int) controlPanelY + 188);
        textRenderer.draw("R - Rain", (int) controlPanelX + 12, (int) controlPanelY + 206);
        textRenderer.draw("M - Audio", (int) controlPanelX + 12, (int) controlPanelY + 224);
        textRenderer.draw("LMB Drag - Camera", (int) controlPanelX + 168, (int) controlPanelY + 224);
        textRenderer.endRendering();

        largeTextRenderer.beginRendering(width, height);
        largeTextRenderer.setColor(0.88f, 0.92f, 0.98f, 0.98f);
        largeTextRenderer.draw("GEAR", (int) drivePanelX + 258, (int) drivePanelY + 92);
        largeTextRenderer.setColor(0.95f, 0.96f, 0.98f, 1.0f);
        largeTextRenderer.draw(truck.getCurrentGearLabel(), (int) drivePanelX + 300, (int) drivePanelY + 48);
        if (truck.isEngineStalled()) {
            largeTextRenderer.setColor(0.92f, 0.28f, 0.22f, 0.98f);
            largeTextRenderer.draw("STALL", (int) drivePanelX + 246, (int) drivePanelY + 18);
        }
        largeTextRenderer.endRendering();
    }

    private void drawScreenVignette(int width, int height) {
        float baseAlpha = 0.22f + currentNightFactor * 0.35f;
        float vw = Math.min(width, height) * 0.42f;
        float vh = Math.min(width, height) * 0.42f;

        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBegin(GL2.GL_QUADS);
        // left
        gl.glColor4f(0.0f, 0.0f, 0.0f, baseAlpha); gl.glVertex2f(0, 0);
        gl.glColor4f(0.0f, 0.0f, 0.0f, 0.0f);       gl.glVertex2f(vw, 0);
        gl.glColor4f(0.0f, 0.0f, 0.0f, 0.0f);       gl.glVertex2f(vw, height);
        gl.glColor4f(0.0f, 0.0f, 0.0f, baseAlpha); gl.glVertex2f(0, height);
        // right
        gl.glColor4f(0.0f, 0.0f, 0.0f, 0.0f);       gl.glVertex2f(width - vw, 0);
        gl.glColor4f(0.0f, 0.0f, 0.0f, baseAlpha); gl.glVertex2f(width, 0);
        gl.glColor4f(0.0f, 0.0f, 0.0f, baseAlpha); gl.glVertex2f(width, height);
        gl.glColor4f(0.0f, 0.0f, 0.0f, 0.0f);       gl.glVertex2f(width - vw, height);
        // bottom
        gl.glColor4f(0.0f, 0.0f, 0.0f, baseAlpha); gl.glVertex2f(0, 0);
        gl.glColor4f(0.0f, 0.0f, 0.0f, baseAlpha); gl.glVertex2f(width, 0);
        gl.glColor4f(0.0f, 0.0f, 0.0f, 0.0f);       gl.glVertex2f(width, vh);
        gl.glColor4f(0.0f, 0.0f, 0.0f, 0.0f);       gl.glVertex2f(0, vh);
        // top
        gl.glColor4f(0.0f, 0.0f, 0.0f, 0.0f);       gl.glVertex2f(0, height - vh);
        gl.glColor4f(0.0f, 0.0f, 0.0f, 0.0f);       gl.glVertex2f(width, height - vh);
        gl.glColor4f(0.0f, 0.0f, 0.0f, baseAlpha); gl.glVertex2f(width, height);
        gl.glColor4f(0.0f, 0.0f, 0.0f, baseAlpha); gl.glVertex2f(0, height);
        gl.glEnd();
    }

    private void drawSpeedEffect(int width, int height, float speedKmh) {
        float speedRatio = clamp01(speedKmh / 110.0f);
        if (speedRatio < 0.45f) return;
        float t = (speedRatio - 0.45f) / 0.55f;
        float alpha = t * t * 0.50f;

        float cx = width * 0.5f;
        float cy = height * 0.5f;
        float radius = (float) Math.sqrt(width * width + height * height);
        int numRays = 20;

        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glBegin(GL2.GL_TRIANGLES);
        for (int i = 0; i < numRays; i++) {
            double a0 = i * Math.PI * 2.0 / numRays;
            double a1 = (i + 0.45) * Math.PI * 2.0 / numRays;
            double a2 = (i + 0.55) * Math.PI * 2.0 / numRays;
            // center (transparent)
            gl.glColor4f(0.0f, 0.0f, 0.0f, 0.0f);
            gl.glVertex2f(cx, cy);
            // outer edge (dark)
            gl.glColor4f(0.0f, 0.0f, 0.0f, alpha);
            gl.glVertex2f(cx + (float) Math.cos(a1) * radius, cy + (float) Math.sin(a1) * radius);
            gl.glVertex2f(cx + (float) Math.cos(a2) * radius, cy + (float) Math.sin(a2) * radius);
        }
        gl.glEnd();
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
    }

    private void drawLensFlare(GLAutoDrawable drawable, double[] mvMatrix, double[] projMatrix, int[] viewport) {
        float[] sunPos = world.getSunWorldPosition();
        float sunIntensity = sunPos[3];
        if (sunIntensity < 0.08f) return;

        // project sun 3D position to screen space
        double[] winXYZ = new double[3];
        glu.gluProject(sunPos[0], sunPos[1], sunPos[2],
                       mvMatrix, 0, projMatrix, 0, viewport, 0, winXYZ, 0);

        // winXYZ[2] is depth; if outside [0,1] sun is clipped
        if (winXYZ[2] < 0.0 || winXYZ[2] > 1.0) return;

        int width  = drawable.getSurfaceWidth();
        int height = drawable.getSurfaceHeight();

        float sx = (float) winXYZ[0];
        float sy = (float) winXYZ[1];

        // check sun is on screen
        if (sx < -200 || sx > width + 200 || sy < -200 || sy > height + 200) return;

        float cx = width * 0.5f;
        float cy = height * 0.5f;
        float axisX = cx - sx;
        float axisY = cy - sy;

        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPushMatrix();
        gl.glLoadIdentity();
        gl.glOrtho(0, width, 0, height, -1, 1);
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPushMatrix();
        gl.glLoadIdentity();

        gl.glDisable(GL2.GL_LIGHTING);
        gl.glDisable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_BLEND);
        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE);

        // flare elements: offset along sun→center axis, size, rgba
        float[][] flares = {
            // offset,  size,   r,    g,    b,    a
            {  0.00f,  90f, 1.00f, 0.95f, 0.70f, 0.55f },  // main glow at sun
            {  0.20f,  28f, 0.80f, 0.60f, 1.00f, 0.35f },  // small blue
            {  0.40f,  55f, 1.00f, 0.80f, 0.40f, 0.22f },  // mid orange
            {  0.60f,  18f, 0.60f, 0.80f, 1.00f, 0.30f },  // small cyan
            {  0.80f,  42f, 1.00f, 0.50f, 0.20f, 0.18f },  // orange ring
            {  1.00f,  14f, 0.70f, 0.60f, 1.00f, 0.28f },  // small violet at center
            {  1.20f,  35f, 0.90f, 0.90f, 1.00f, 0.15f },  // far white
            {  1.50f,  22f, 1.00f, 0.70f, 0.30f, 0.20f },  // far orange
        };

        for (float[] f : flares) {
            float px = sx + axisX * f[0];
            float py = sy + axisY * f[0];
            float s  = f[1] * sunIntensity;
            gl.glColor4f(f[2], f[3], f[4], f[5] * sunIntensity);
            gl.glBegin(GL2.GL_QUADS);
            gl.glVertex2f(px - s, py - s);
            gl.glVertex2f(px + s, py - s);
            gl.glVertex2f(px + s, py + s);
            gl.glVertex2f(px - s, py + s);
            gl.glEnd();
        }

        gl.glBlendFunc(GL2.GL_SRC_ALPHA, GL2.GL_ONE_MINUS_SRC_ALPHA);
        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glEnable(GL2.GL_LIGHTING);
        gl.glMatrixMode(GL2.GL_PROJECTION);
        gl.glPopMatrix();
        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glPopMatrix();
    }

    @Override
    public void dispose(GLAutoDrawable drawable) {
        if (audioEngine != null) {
            audioEngine.shutdown();
            audioEngine = null;
        }
        if (textRenderer != null) {
            textRenderer.dispose();
            textRenderer = null;
        }
        if (largeTextRenderer != null) {
            largeTextRenderer.dispose();
            largeTextRenderer = null;
        }
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

    @Override
    public void mouseClicked(MouseEvent e) {
        // No action needed.
    }

    @Override
    public void mousePressed(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            leftMouseDragging = true;
            lastMouseX = e.getX();
            lastMouseY = e.getY();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (e.getButton() == MouseEvent.BUTTON1) {
            leftMouseDragging = false;
        }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
        // No action needed.
    }

    @Override
    public void mouseExited(MouseEvent e) {
        leftMouseDragging = false;
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (!leftMouseDragging) {
            return;
        }

        int mouseX = e.getX();
        int mouseY = e.getY();
        int dx = mouseX - lastMouseX;
        int dy = mouseY - lastMouseY;
        lastMouseX = mouseX;
        lastMouseY = mouseY;

        if (cabinViewEnabled) {
            firstPersonLookYawDeg = clamp(-120.0f, 120.0f, firstPersonLookYawDeg + dx * 0.22f);
            firstPersonLookPitchDeg = clamp(-55.0f, 55.0f, firstPersonLookPitchDeg - dy * 0.18f);
        } else {
            thirdPersonOrbitYawDeg += dx * 0.26f;
            thirdPersonOrbitPitchDeg = clamp(-25.0f, 35.0f, thirdPersonOrbitPitchDeg - dy * 0.18f);
        }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
        // No action needed.
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
        } else if (keyCode == KeyEvent.VK_SHIFT && pressed) {
            if (world != null) {
                world.getTruck().shiftUp();
            }
        } else if (keyCode == KeyEvent.VK_CONTROL && pressed) {
            if (world != null) {
                world.getTruck().shiftDown();
            }
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
        } else if (keyCode == KeyEvent.VK_M && pressed) {
            audioEnabled = !audioEnabled;
            if (audioEngine != null) {
                audioEngine.setEnabled(audioEnabled);
            }
        }
    }

    private void applyEnvironmentPreset() {
        float phase = currentCelestialPhase;

        float[][] clearColorKeys = {
                { 0.72f, 0.64f, 0.53f, 1.0f },
                { 0.58f, 0.79f, 0.98f, 1.0f },
                { 0.93f, 0.58f, 0.35f, 1.0f },
            { 0.008f, 0.010f, 0.018f, 1.0f },
                { 0.72f, 0.64f, 0.53f, 1.0f }
        };

        float[][] fogColorKeys = {
                { 0.70f, 0.60f, 0.50f, 1.0f },
                { 0.65f, 0.83f, 0.98f, 1.0f },
                { 0.86f, 0.52f, 0.35f, 1.0f },
            { 0.006f, 0.008f, 0.015f, 1.0f },
                { 0.70f, 0.60f, 0.50f, 1.0f }
        };

        float[][] ambientKeys = {
                { 0.28f, 0.26f, 0.24f, 1.0f },
                { 0.35f, 0.35f, 0.35f, 1.0f },
                { 0.42f, 0.31f, 0.25f, 1.0f },
            { 0.008f, 0.008f, 0.014f, 1.0f },
                { 0.28f, 0.26f, 0.24f, 1.0f }
        };

        float[][] diffuseKeys = {
                { 0.58f, 0.52f, 0.46f, 1.0f },
                { 0.90f, 0.90f, 0.90f, 1.0f },
                { 0.92f, 0.62f, 0.43f, 1.0f },
            { 0.022f, 0.025f, 0.040f, 1.0f },
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
        currentHeadlightIntensity = headlightsEnabled ? (1.20f + currentNightFactor * 3.20f) : 0.0f;

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
        float intensity = Math.max(0.0f, Math.min(4.5f, currentHeadlightIntensity));

        float[] diffuse = {
            1.85f * intensity,
            1.72f * intensity,
            1.45f * intensity,
            1.0f
        };
        float[] specular = {
            1.20f * intensity,
            1.10f * intensity,
            0.92f * intensity,
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
            -0.14f,
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

    private float clamp(float min, float max, float value) {
        return Math.max(min, Math.min(max, value));
    }

    private float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    private float[] cross(float[] a, float[] b) {
        return new float[] {
            a[1] * b[2] - a[2] * b[1],
            a[2] * b[0] - a[0] * b[2],
            a[0] * b[1] - a[1] * b[0]
        };
    }

    private float vectorLength(float[] v) {
        return (float) Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
    }

    private float[] normalizeVector(float[] v) {
        float len = vectorLength(v);
        if (len < 0.0001f) {
            return new float[] { 0.0f, 0.0f, 0.0f };
        }
        return new float[] { v[0] / len, v[1] / len, v[2] / len };
    }

    private float[] rotateAroundAxis(float[] v, float[] axis, float angleRad) {
        float[] n = normalizeVector(axis);
        if (vectorLength(n) < 0.0001f) {
            return new float[] { v[0], v[1], v[2] };
        }

        float cos = (float) Math.cos(angleRad);
        float sin = (float) Math.sin(angleRad);
        float dot = v[0] * n[0] + v[1] * n[1] + v[2] * n[2];

        return new float[] {
            v[0] * cos + (n[1] * v[2] - n[2] * v[1]) * sin + n[0] * dot * (1.0f - cos),
            v[1] * cos + (n[2] * v[0] - n[0] * v[2]) * sin + n[1] * dot * (1.0f - cos),
            v[2] * cos + (n[0] * v[1] - n[1] * v[0]) * sin + n[2] * dot * (1.0f - cos)
        };
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
