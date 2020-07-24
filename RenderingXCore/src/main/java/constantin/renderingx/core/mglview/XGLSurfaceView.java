package constantin.renderingx.core.mglview;

import android.content.Context;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import javax.microedition.khronos.opengles.GL10;

import static android.opengl.EGL14.EGL_DEFAULT_DISPLAY;
import static android.opengl.EGL14.EGL_NO_DISPLAY;
import static android.opengl.EGL14.EGL_NO_SURFACE;
import static android.opengl.EGL14.EGL_NO_CONTEXT;

public class XGLSurfaceView extends SurfaceView implements LifecycleObserver, SurfaceHolder.Callback {
    final AppCompatActivity activity;
    EGLDisplay eglDisplay = EGL_NO_DISPLAY;
    EGLSurface eglSurface = EGL_NO_SURFACE;
    EGLContext eglContext = EGL_NO_CONTEXT;
    EGLConfig eglConfig = null;
    private final Object eglSurfaceAvailable=new Object();

    private Thread mOpenGLThread;
    private GLSurfaceView.Renderer mRenderer;
    private Renderer2 mRenderer2;
    private int SURFACE_W,SURFACE_H;
    private boolean firstTimeSurfaceBound=true;

    public XGLSurfaceView(final Context context){
        super(context);
        activity=((AppCompatActivity)context);
        ((AppCompatActivity)context).getLifecycle().addObserver(this);
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
    }
    public XGLSurfaceView(Context context, AttributeSet attrs) {
        super(context,attrs);
        activity=((AppCompatActivity)context);
        ((AppCompatActivity)context).getLifecycle().addObserver(this);
        SurfaceHolder holder = getHolder();
        holder.addCallback(this);
    }

    public void setRenderer(final GLSurfaceView.Renderer renderer){
        this.mRenderer=renderer;
    }
    public void setRenderer(final Renderer2 renderer2){
        this.mRenderer2=renderer2;
    }

    /**
     * Create the OpenGL context, but not the EGL Surface since I have to wait for the
     * android.view.SurfaceHolder.Callback until the native window is available
     */
    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    private void onCreate() {
        log("onCreate");
        eglDisplay = EGL14.eglGetDisplay(EGL_DEFAULT_DISPLAY);
        int[] major = new int[]{0};
        int[] minor = new int[]{0};
        EGL14.eglInitialize(eglDisplay, major, 0, minor, 0);
        final EGLConfigChooser mEGLConfigChooser = new XEGLConfigChooser(false, 0, true);
        eglConfig = mEGLConfigChooser.chooseConfig(eglDisplay);
        final int[] contextAttributes = new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        // https://www.khronos.org/registry/EGL/sdk/docs/man/html/eglCreateContext.xhtml
        eglContext = EGL14.eglCreateContext(eglDisplay, eglConfig, EGL_NO_CONTEXT, contextAttributes, 0);
        if (eglContext==EGL_NO_CONTEXT) {
            throw new AssertionError("Cannot create eglContext");
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    private void onResume(){
        log("onResume");
        mOpenGLThread=new Thread(new Runnable() {
            @Override
            public void run() {
                if(firstTimeSurfaceBound){
                    if(mRenderer2!=null){
                        mRenderer2.onContextCreated();
                    }
                    firstTimeSurfaceBound=false;
                }
                synchronized (eglSurfaceAvailable){
                    if(eglSurface==EGL_NO_SURFACE){
                        try {
                            eglSurfaceAvailable.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                            return;
                        }
                    }
                }
                makeCurrent(eglSurface);
                if(mRenderer!=null){
                    mRenderer.onSurfaceCreated(null,null);
                    mRenderer.onSurfaceChanged(null,SURFACE_W,SURFACE_H);
                }

                while (!Thread.currentThread().isInterrupted()){
                    //System.out.println("Render");
                    if(mRenderer!=null){
                        mRenderer.onDrawFrame(null);
                    }
                    if(mRenderer2!=null){
                        mRenderer2.onDrawFrame();
                    }
                    if(!EGL14.eglSwapBuffers(eglDisplay,eglSurface)){
                        System.out.println("Cannot swap buffers");
                    }
                }
                boolean result= EGL14.eglMakeCurrent(eglDisplay,EGL_NO_SURFACE,EGL_NO_SURFACE,EGL_NO_CONTEXT);
                if(!result){
                    throw new AssertionError("Cannot unbind surface");
                }

            }
        });
        mOpenGLThread.start();
    }


    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    private void onPause(){
        log("onPause");
        mOpenGLThread.interrupt();
        try {
            mOpenGLThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    private void onDestroy(){
        log("onDestroy");
        EGL14.eglDestroyContext(eglDisplay, eglContext);
        EGL14.eglTerminate(eglDisplay);
    }


    private void makeCurrent(EGLSurface surface) {
        log("makeCurrent");
        boolean result= EGL14.eglMakeCurrent(eglDisplay, surface, surface,eglContext);
        if(!result){
            throw new AssertionError("Cannot make surface current "+surface);
        }
    }


    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        System.out.println("X surfaceCreated");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        SURFACE_W=width;
        SURFACE_H=height;
        System.out.println("X surfaceChanged");

        //if(activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED)){
        //   System.out.println("Got surface before onCreate()");
        //   createEGLSurface(holder.getSurface());
        //}
        if(eglSurface!=EGL_NO_SURFACE){
            throw new AssertionError("Changing Surface is not supported");
        }
        if(!activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.CREATED)){
            throw new AssertionError("Got surface before onCreate()");
        }
        synchronized (eglSurfaceAvailable){
            eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, eglConfig,holder.getSurface(),null,0);
            if(eglSurface==EGL_NO_SURFACE){
                throw new AssertionError("Cannot create window surface");
            }
            eglSurfaceAvailable.notify();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        System.out.println("X surfaceDestroyed");
        if(activity.getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED)){
            throw new AssertionError("Destroyed surface before onPause()");
        }
        EGL14.eglDestroySurface(eglDisplay, eglSurface);
        eglSurface=EGL_NO_SURFACE;
    }

    static void log(String message){
        Log.d("MyGLView",message);
    }


    /**
     * An interface for choosing an EGLConfig configuration from a list of
     * potential configurations.
     * <p>
     * This interface must be implemented by clients wishing to call
     * {@link GLSurfaceView#setEGLConfigChooser(GLSurfaceView.EGLConfigChooser)}
     */
    public interface EGLConfigChooser {
        /**
         * Choose a configuration from the list. Implementors typically
         * implement this method by calling
         * {@link EGL14#eglChooseConfig} and iterating through the results. Please consult the
         * EGL specification available from The Khronos Group to learn how to call eglChooseConfig.
         * @param display the current display.
         * @return the chosen configuration.
         */
        EGLConfig chooseConfig(EGLDisplay display);
    }


    public interface Renderer2{
        // Called as soon as the OpenGL context is created
        // The lifetime of the OpenGL context is tied to the lifetime of the Activity (onCreate / onDestroy)
        // Therefore this callback is called at most once
        void onContextCreated();
        // Called repeatedly in between onResume() / onPause()
        void onDrawFrame();
        // Called once the opengl context has to be destroyed,
        // but here the context is still bound for cleanup operations
        //void onContextDestroyed();

    }
}
