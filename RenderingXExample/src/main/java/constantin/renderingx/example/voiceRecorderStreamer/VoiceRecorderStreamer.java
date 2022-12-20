package constantin.renderingx.example.voiceRecorderStreamer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.util.Log;

import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VoiceRecorderStreamer {
    private float[] audioRecordBuffer; // short type buffer used for 16bit(2bytes) encoding
    private AudioRecord audioRecord;
    private Context context;

    private static final int audioSource = MediaRecorder.AudioSource.MIC;
    private static final int sampleRateInHz = 16000;
    private static final int channelConfig = AudioFormat.CHANNEL_IN_MONO;
    private static final int audioFormat = AudioFormat.ENCODING_PCM_FLOAT;
    private int bufferSizeInFloats;

    private String ip = ""; // Server's IP
    private int port = -1;

    Thread threadSendToServer;

    private boolean isRecording = false;

    public VoiceRecorderStreamer(Context context, String ip, int port) {
        this.context = context;
        bufferSizeInFloats = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat) * 4;
        this.ip = ip;
        this.port = port;
    }

    public void startRecording() {
        //TODO: Fix RunTimeError occrured when starting app without getting RECORD_AUDIO permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.RECORD_AUDIO}, 100);

        audioRecordBuffer = new float[bufferSizeInFloats];
        audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInFloats);

        audioRecord.startRecording();
        isRecording = true;

        threadSendToServer = new Thread(new Runnable() {
            @RequiresApi(api = Build.VERSION_CODES.M)
            @Override
            public void run() {
                // UDP Socket
                try {
                    DatagramSocket socket = new DatagramSocket();
                    while(isRecording) {
                        int bytesRead = audioRecord.read(audioRecordBuffer, 0, bufferSizeInFloats, AudioRecord.READ_BLOCKING);
                        Log.d("Recording Bytes", String.valueOf(bytesRead));
                        byte[] audioRecordBufferInBytes = new byte[bytesRead * 4];

//                        for(int i=0; i<bytesRead; i++) {
//                            audioRecordBufferInBytes[2*i] = (byte) (0xff & audioRecordBuffer[i]);
//                            audioRecordBufferInBytes[2*i+1] = (byte) (audioRecordBuffer[i] >> 8);
//                        }
                        for(int i=0; i<bytesRead; i++) {
                            audioRecordBufferInBytes[4*i] = (byte) (0xff & Float.floatToIntBits(audioRecordBuffer[i]));
                            audioRecordBufferInBytes[4*i+1] = (byte) (0xff & (Float.floatToIntBits(audioRecordBuffer[i]) >> 8));
                            audioRecordBufferInBytes[4*i+2] = (byte) (0xff & (Float.floatToIntBits(audioRecordBuffer[i]) >> 16));
                            audioRecordBufferInBytes[4*i+3] = (byte) (Float.floatToIntBits(audioRecordBuffer[i]) >> 24);
                        }

                        DatagramPacket packet = new DatagramPacket(audioRecordBufferInBytes, bytesRead * 4, InetAddress.getByName(ip), port);
                        socket.send(packet);
                    }
                    socket.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        threadSendToServer.start();
    }

    //TODO: Call stopRecording on disconnection
    public void stopRecording() throws InterruptedException {
        isRecording = false;
        threadSendToServer.join();
        audioRecord.stop();
        audioRecord.release();
    }
}
