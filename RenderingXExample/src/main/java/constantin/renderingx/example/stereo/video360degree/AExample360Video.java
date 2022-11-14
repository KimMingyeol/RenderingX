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
    public static final int SPHERE_MODE_GVR_EQUIRECTANGULAR=0;
    public static final int SPHERE_MODE_INSTA360_TEST=1;
    public static final int SPHERE_MODE_INSTA360_TEST2=2;
    //Default mode is 0 (test VDDC)
    public static final String KEY_SPHERE_MODE ="KEY_SPHERE_MODE";
    public static final String KEY_VIDEO_FILENAME="KEY_VIDEO_FILENAME";
    // Only one of these two is in use at the same time
    private static final boolean USE_GOOGLE_EXO_PLAYER_INSTEAD = true;
    // ExoPlayer is the better choice for file playback, but my VideoPlayer
    // Is the better choice for low latency h264 live video playback
    private VideoPlayer videoPlayer;
    private ExoPlayer simpleExoPlayer;

    private GStreamerSurfaceView gStreamerSurfaceView;
    private D3TelepresenceAndroid d3TelepresenceAndroid;
    private VoiceRecorderStreamer voiceRecorderStreamer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle bundle=getIntent().getExtras();
        final int SPHERE_MODE=bundle.getInt(KEY_SPHERE_MODE,0);
        final String VIDEO_FILENAME=bundle.getString(KEY_VIDEO_FILENAME);
        //start initialization
        final VrView mVrView=new VrView(this);
        //mVrView.enableSuperSync();

        gStreamerSurfaceView = new GStreamerSurfaceView(this);

        d3TelepresenceAndroid = new D3TelepresenceAndroid(this);
        d3TelepresenceAndroid.startSync();

        voiceRecorderStreamer = new VoiceRecorderStreamer(this);
        voiceRecorderStreamer.startRecording();

        // Use one of both ! Default to the player from VideoCore
        if(USE_GOOGLE_EXO_PLAYER_INSTEAD){
           simpleExoPlayer=createAndConfigureExoPlayer(this);
//           simpleExoPlayer.setVideoSurfaceView(gStreamerSurfaceView);
        }else{
            VideoSettings.setVS_SOURCE(this, VideoSettings.VS_SOURCE.ASSETS);
            VideoSettings.setVS_ASSETS_FILENAME_TEST_ONLY(this,VIDEO_FILENAME);
            VideoSettings.setVS_FILE_ONLY_LIMIT_FPS(this,40);
            videoPlayer=new VideoPlayer(this);
        }
        final ISurfaceTextureAvailable iSurfaceTextureAvailableExoPlayer=new ISurfaceTextureAvailable() {
            @Override
            public void surfaceTextureCreated(SurfaceTexture surfaceTexture, Surface surface) {
                simpleExoPlayer.setVideoSurface(surface);
            }
            @Override
            public void surfaceTextureDestroyed() {
                simpleExoPlayer.setVideoSurface(null);
            }
        };

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

        Renderer360Video renderer = new Renderer360Video(this,mVrView.getGvrApi(), SPHERE_MODE);
//        mVrView.getPresentationView().setRenderer(renderer,USE_GOOGLE_EXO_PLAYER_INSTEAD ? iSurfaceTextureAvailableExoPlayer : videoPlayer.configure2());
        mVrView.getPresentationView().setRenderer(renderer,iSurfaceTextureAvailableGstreamer);
        mVrView.getPresentationView().setISecondaryContext(renderer);
        setContentView(mVrView);
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(simpleExoPlayer!=null){
            simpleExoPlayer.setPlayWhenReady(true);
        }
    }
    @Override
    protected void onPause(){
        super.onPause();
        if(simpleExoPlayer!=null){
            simpleExoPlayer.setPlayWhenReady(false);
        }
    }

    // ExoPlayer is a better choice than the Android MediaPlayer
    private static ExoPlayer createAndConfigureExoPlayer(final Context context){
        ExoPlayer exoPlayer=new ExoPlayer.Builder(context).build();
        final Uri uri = RawResourceDataSource.buildRawResourceUri(R.raw.test_room1_1920mono);
        final MediaItem mediaItem = MediaItem.fromUri(uri);
        exoPlayer.setMediaItem(mediaItem);
//        exoPlayer.setRepeatMode(Player.REPEAT_MODE_ALL);
        exoPlayer.prepare();
        return exoPlayer;
    }

    //TODO: stop connection on D3TelepresenceAndroid & VoiceRecorderStreamer on VR screen exit
}