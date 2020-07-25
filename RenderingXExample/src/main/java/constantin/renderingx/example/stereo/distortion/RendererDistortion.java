package constantin.renderingx.example.stereo.distortion;

import android.annotation.SuppressLint;
import android.content.Context;
import android.opengl.GLSurfaceView;

import com.google.vr.ndk.base.GvrApi;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import constantin.renderingx.core.MVrHeadsetParams;
import constantin.renderingx.core.mglview.XGLSurfaceView;


//See native code for documentation

public class RendererDistortion implements /*GLSurfaceView.Renderer,*/XGLSurfaceView.Renderer2 {
    static {
        System.loadLibrary("example-1");
    }
    private native long nativeConstruct(Context context,long nativeGvrContext);
    private native void nativeDelete(long p);
    private native void nativeOnSurfaceCreated(long p,final Context context);
    private native void nativeOnDrawFrame(long p);
    private native void nativeUpdateHeadsetParams(long nativePointer,final MVrHeadsetParams p);

    private final Context mContext;
    private final long nativeRenderer;

    @SuppressLint("ApplySharedPref")
    public RendererDistortion(final Context context, final GvrApi gvrApi){
        mContext=context;
        nativeRenderer=nativeConstruct(context,
                gvrApi.getNativeGvrContext());
        final MVrHeadsetParams params=new MVrHeadsetParams(context);
        nativeUpdateHeadsetParams(nativeRenderer,params);
    }

    @Override
    public void onContextCreated(int width,int height) {
        nativeOnSurfaceCreated(nativeRenderer,mContext);
    }

    @Override
    public void onDrawFrame() {
        nativeOnDrawFrame(nativeRenderer);
    }

    @Override
    protected void finalize() throws Throwable {
        try {
            nativeDelete(nativeRenderer);
        } finally {
            super.finalize();
        }
    }
}
