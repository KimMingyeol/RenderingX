package constantin.renderingx.example.stereo.video360degree;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.Voice;
import android.view.Surface;

import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.upstream.RawResourceDataSource;

import constantin.renderingx.core.views.VrActivity;
import constantin.renderingx.core.views.VrView;
import constantin.renderingx.example.R;
import constantin.renderingx.example.d3_telepresence_android.D3TelepresenceAndroid;
import constantin.renderingx.example.voiceRecorderStreamer.VoiceRecorderStreamer;
import constantin.video.core.gl.ISurfaceTextureAvailable;
import constantin.video.core.player.VideoPlayer;
import constantin.video.core.player.VideoSettings;

import com.example.gstreamer_android.GStreamerSurfaceView;

//Uses the LiveVideo10ms VideoCore lib which is intended for live streaming, not file playback.
//I recommend using android MediaPlayer if only playback from file is needed

//See native code (renderer) for documentation
public class AExample360Video extends VrActivity {
    private static final String TAG="AExampleVRRendering";
    public static final int SPHERE_MODE_GVR_EQUIRECTANGULAR = 0;
    //Default mode is 0 (test VDDC)
    public static final String KEY_SPHERE_MODE = "KEY_SPHERE_MODE";

    private GStreamerSurfaceView gStreamerSurfaceView;
    private D3TelepresenceAndroid d3TelepresenceAndroid;
    private VoiceRecorderStreamer voiceRecorderStreamer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle bundle=getIntent().getExtras();
        final int SPHERE_MODE=bundle.getInt(KEY_SPHERE_MODE,0);
        //start initialization
        final VrView mVrView=new VrView(this);

        gStreamerSurfaceView = new GStreamerSurfaceView(this);

        d3TelepresenceAndroid = new D3TelepresenceAndroid(this);
        d3TelepresenceAndroid.startSync();

//        voiceRecorderStreamer = new VoiceRecorderStreamer(this);
//        voiceRecorderStreamer.startRecording();

        final ISurfaceTextureAvailable iSurfaceTextureAvailableGstreamer = new ISurfaceTextureAvailable() {
            @Override
            public void surfaceTextureCreated(SurfaceTexture surfaceTexture, Surface surface) {
                gStreamerSurfaceView.surfaceChanged(surface);
            }

            @Override
            public void surfaceTextureDestroyed() {
                gStreamerSurfaceView.surfaceDestroyed();
            }
        };

        Renderer360Video renderer = new Renderer360Video(this, mVrView.getGvrApi(), SPHERE_MODE);
        mVrView.getPresentationView().setRenderer(renderer, iSurfaceTextureAvailableGstreamer);
        mVrView.getPresentationView().setISecondaryContext(renderer);
        setContentView(mVrView);
    }

    //TODO: stop connection on D3TelepresenceAndroid & VoiceRecorderStreamer on VR screen exit
}