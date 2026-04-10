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

        gl.glEnable(GL2.GL_DEPTH_TEST);
        gl.glDisable(GL2.GL_CULL_FACE);

        gl.glEnable(GL2.GL_LIGHTING);
        gl.glEnable(GL2.GL_LIGHT0);
        gl.glEnable(GL2.GL_COLOR_MATERIAL);
        gl.glColorMaterial(GL2.GL_FRONT_AND_BACK, GL2.GL_AMBIENT_AND_DIFFUSE);

        float[] ambient = { 0.35f, 0.35f, 0.35f, 1.0f };
        float[] diffuse = { 0.9f, 0.9f, 0.9f, 1.0f };
        float[] lightPosition = { 120.0f, 180.0f, 120.0f, 1.0f };
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_AMBIENT, ambient, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_DIFFUSE, diffuse, 0);
        gl.glLightfv(GL2.GL_LIGHT0, GL2.GL_POSITION, lightPosition, 0);

        String renderer = gl.glGetString(GL2.GL_RENDERER);
        setTitle("Scania Driver | " + renderer);
    }

    @Override
    public void display(GLAutoDrawable drawable) {
        gl.glClearColor(0.58f, 0.79f, 0.98f, 1.0f);
        gl.glClear(GL2.GL_COLOR_BUFFER_BIT | GL2.GL_DEPTH_BUFFER_BIT);

        gl.glMatrixMode(GL2.GL_MODELVIEW);
        gl.glLoadIdentity();

        world.setControls(forwardPressed, backwardPressed, leftPressed, rightPressed);
        world.update();

        Truck truck = world.getTruck();
        float cameraDistance = 11.0f;
        float cameraHeight = 5.0f;

        float headingRadians = (float) Math.toRadians(truck.getAngle());
        float headingX = (float) Math.sin(headingRadians);
        float headingZ = (float) Math.cos(headingRadians);

        float cameraX = truck.getX() - headingX * cameraDistance;
        float cameraY = truck.getY() + cameraHeight;
        float cameraZ = truck.getZ() - headingZ * cameraDistance;

        float targetX = truck.getX() + headingX * 2.2f;
        float targetY = truck.getY() + 1.05f;
        float targetZ = truck.getZ() + headingZ * 2.2f;
        glu.gluLookAt(cameraX, cameraY, cameraZ, targetX, targetY, targetZ, 0.0f, 1.0f, 0.0f);

        world.draw(gl);

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
        if (keyCode == KeyEvent.VK_UP) {
            forwardPressed = pressed;
        } else if (keyCode == KeyEvent.VK_DOWN) {
            backwardPressed = pressed;
        } else if (keyCode == KeyEvent.VK_LEFT) {
            leftPressed = pressed;
        } else if (keyCode == KeyEvent.VK_RIGHT) {
            rightPressed = pressed;
        }
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
