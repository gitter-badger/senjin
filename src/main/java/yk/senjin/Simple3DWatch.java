package yk.senjin;

import org.lwjgl.LWJGLException;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.DisplayMode;
import yk.jcommon.fastgeom.Matrix4;
import yk.jcommon.fastgeom.Quaternionf;
import yk.jcommon.fastgeom.Vec2f;
import yk.jcommon.fastgeom.Vec3f;
import yk.jcommon.utils.Cam;
import yk.jcommon.utils.Threads;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.util.glu.GLU.gluPerspective;
import static yk.jcommon.fastgeom.Matrix4.perspective;

/**
 * Created with IntelliJ IDEA.
 * User: yuri
 * Date: 4/3/14
 * Time: 8:43 PM
 */
public class Simple3DWatch {
    //TODO import some fps-view
    //TODO import nyan fps-view
    //TODO connect to paperpointer
    //TODO screen mouse 2 XY mouse

    //TODO onKeyDown
    //TODO onKeyUp

    private boolean firstFrame = true;

    public boolean SIMPLE_AA = true;
    SimpleAntiAliasing simpleAA;

    //TODO extract viewport
    public final Cam cam = new Cam();
    public int w, h;
    Vec2f mousePressedAt;
    Vec2f mouseCur;
    Quaternionf cameraOld;
    public final float magnifier = 1;
    float camPitch = 0;
    float camYaw = 0;
    public Matrix4 camModelViewMatrix;
    public Matrix4 camModelViewProjectionMatrix;
    public Matrix4 camNormalMatrix;//TODO or Matrix3?

    public Vec3f backgroundColor = new Vec3f(0, 0, 0);

    public SkyBox skyBox;

//    public static void main(String[] args) throws LWJGLException {
//        new Simple3DWatch(512, 512, true);
//    }

    public Simple3DWatch() {
        this(800, 600, true);
    }

    public Simple3DWatch(int w, int h, boolean createThread) {
        this.w = w;
        this.h = h;
        if (SIMPLE_AA) simpleAA = new SimpleAntiAliasing();

        final Simple3DWatch THIS = this;
        cam.lookAt = new Vec3f(0, 0, 100);


        if (createThread) Threads.tick(new Threads.Tickable() {
            @Override
            public void tick(float dt) throws Exception {
                commonTick(dt);
                THIS.tick(dt);
                //aa
                if (SIMPLE_AA) simpleAA.renderFBO();
                Display.update();
                if (Keyboard.isKeyDown(Keyboard.KEY_ESCAPE) || Display.isCloseRequested()) exit = true;
            }

        }, 10);
    }

    protected void commonTick(float dt) throws LWJGLException {
        if (firstFrame) {
            firstFrame = false;
            DisplayMode[] modes = Display.getAvailableDisplayModes();
            Display.setDisplayMode(new DisplayMode(w, h));
//                    Display.setDisplayMode(modes[modes.length - 1]);
            Display.create();
            Display.makeCurrent();
            //aa
            if (SIMPLE_AA) simpleAA.initFBO(w*2, h*2);

            glEnable(GL_BLEND);
            glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

            firstFrame();
        }
        //aa
        if (SIMPLE_AA) simpleAA.switchToFBO();
        mouseCur = new Vec2f(Mouse.getX(), Mouse.getY());
        if (Mouse.isButtonDown(0)) {
            if (mousePressedAt == null) {
                mousePressedAt = new Vec2f(Mouse.getX(), Mouse.getY());
                cameraOld = cam.lookRot;
            }
            rotateCam(-(mouseCur.y - mousePressedAt.y) * 0.004f, (mouseCur.x - mousePressedAt.x) * 0.004f);
            mousePressedAt = mouseCur;
        } else {
            mousePressedAt = null;
        }
        cam.lookRot = Quaternionf.fromAngleAxisFast(camPitch, new Vec3f(1, 0, 0))
                .mul(Quaternionf.fromAngleAxisFast(camYaw, new Vec3f(0, 1, 0)))
                .normalized();

        glAlphaFunc ( GL_GREATER, 0.1f) ;
        glEnable ( GL_ALPHA_TEST ) ;

        float camMoveSpeed = 30;
        if (Keyboard.isKeyDown(Keyboard.KEY_LSHIFT)) camMoveSpeed /= 10;
        if (Keyboard.isKeyDown(Keyboard.KEY_W)) cam.lookAt = cam.lookAt.add(cam.lookRot.rotateFast(new Vec3f(0, 0, -camMoveSpeed * dt)));
        if (Keyboard.isKeyDown(Keyboard.KEY_S)) cam.lookAt = cam.lookAt.add(cam.lookRot.rotateFast(new Vec3f(0, 0, camMoveSpeed * dt)));
        if (Keyboard.isKeyDown(Keyboard.KEY_A)) cam.lookAt = cam.lookAt.add(cam.lookRot.rotateFast(new Vec3f(-camMoveSpeed * dt, 0, 0)));
        if (Keyboard.isKeyDown(Keyboard.KEY_D)) cam.lookAt = cam.lookAt.add(cam.lookRot.rotateFast(new Vec3f(camMoveSpeed * dt, 0, 0)));
        if (Keyboard.isKeyDown(Keyboard.KEY_Q)) cam.lookAt = cam.lookAt.add(cam.lookRot.rotateFast(new Vec3f(0, camMoveSpeed * dt, 0)));
        if (Keyboard.isKeyDown(Keyboard.KEY_Z)) cam.lookAt = cam.lookAt.add(cam.lookRot.rotateFast(new Vec3f(0, -camMoveSpeed * dt, 0)));

        if (Keyboard.isKeyDown(Keyboard.KEY_UP)) rotateCam(-dt, 0);
        if (Keyboard.isKeyDown(Keyboard.KEY_DOWN)) rotateCam(dt, 0);
        if (Keyboard.isKeyDown(Keyboard.KEY_LEFT)) rotateCam(0, -dt);
        if (Keyboard.isKeyDown(Keyboard.KEY_RIGHT)) rotateCam(0, dt);

        glClearColor(backgroundColor.x, backgroundColor.y, backgroundColor.z, 1);
        glClear(GL_COLOR_BUFFER_BIT);
        glClear(GL_DEPTH_BUFFER_BIT);
        glBindTexture(GL_TEXTURE_2D, 0);
        glDepthMask(true);

        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        gluPerspective(45.0f, (float) w / h, magnifier, 1000.0f * magnifier);
        resetModelView();

        glDisable(GL_DEPTH_TEST);
        if (skyBox != null) skyBox.render(cam.lookAt);
        glEnable(GL_DEPTH_TEST);

        glLineWidth(3);
        glBegin(GL_LINES);


        float len = 10;
        glColor3f(1, 0, 0);
        glVertex3f(0, 0, 0);
        glVertex3f(len, 0, 0);

        glColor3f(0, 1, 0);
        glVertex3f(0, 0, 0);
        glVertex3f(0, len, 0);

        glColor3f(0, 0, 1);
        glVertex3f(0, 0, 0);
        glVertex3f(0, 0, len);

        glEnd();

        camModelViewMatrix = new Matrix4()
                 .setIdentity()
                 .translate(cam.lookAt.mul(-1))
                 .multiply(cam.lookRot.conjug().toMatrix4());
        camNormalMatrix = camModelViewMatrix
                 .invert()
                 .transpose();
        camModelViewProjectionMatrix = camModelViewMatrix
                 .multiply(perspective(45.0f, (float) w / h, magnifier, 1000.0f * magnifier));
    }

    private void rotateCam(float pitch, float yaw) {
        camPitch += pitch;
        if (camPitch < -Math.PI / 2) camPitch = (float) (-Math.PI / 2);
        if (camPitch > Math.PI / 2) camPitch = (float) (Math.PI / 2);
        camYaw += yaw;
    }

    public void resetModelView() {
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();
        DDDUtils.multMatrix(cam.lookRot.conjug());
        glTranslatef(-cam.lookAt.x, -cam.lookAt.y, -cam.lookAt.z);
    }

    public void tick(float dt) {

    }

    public void firstFrame() {

    }

}
