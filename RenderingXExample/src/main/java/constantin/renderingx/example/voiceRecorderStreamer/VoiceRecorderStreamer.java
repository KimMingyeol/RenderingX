package constantin.renderingx.example.voiceRecorderStreamer;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class VoiceRecorderStreamer {
    private short[] audioRecordBuffer; // short type buffer used for 16bit(2bytes) encoding
    private AudioRecord audioRecord;
    private Context context;

    private static final int audioSource = MediaRecorder.AudioSource.MIC;
    private static final int sampleRateInHz = 44100;
    private static final int channelConfig = AudioFormat.CHANNEL_IN_STEREO;
    private static final int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private int bufferSizeInShorts;

    private String ip = ""; // Server's IP
    private int port = -1;

    Thread threadSendToServer;

    private boolean isRecording = false;

    public VoiceRecorderStreamer(Context context) {
        this.context = context;
        bufferSizeInShorts = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat) * 4;
    }

    public void startRecording() {
        //TODO: Fix RunTimeError occrured when starting app without getting RECORD_AUDIO permission
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions((Activity) context, new String[]{Manifest.permission.RECORD_AUDIO}, 100);

        audioRecordBuffer = new short[bufferSizeInShorts];
        audioRecord = new AudioRecord(audioSource, sampleRateInHz, channelConfig, audioFormat, bufferSizeInShorts);

        audioRecord.startRecording();
        isRecording = true;

        threadSendToServer = new Thread(new Runnable() {
            @Override
            public void run() {
                // UDP Socket
                try {
                    DatagramSocket socket = new DatagramSocket();
                    while(isRecording) {
                        int bytesRead = audioRecord.read(audioRecordBuffer, 0, bufferSizeInShorts);
                        Log.d("Recording Bytes", String.valueOf(bytesRead));
                        byte[] audioRecordBufferInBytes = new byte[bytesRead * 2];

                        for(int i=0; i<bytesRead; i++) {
                            audioRecordBufferInBytes[2*i] = (byte) (0xff & audioRecordBuffer[i]);
                            audioRecordBufferInBytes[2*i+1] = (byte) (audioRecordBuffer[i] >> 8);
                        }

                        DatagramPacket packet = new DatagramPacket(audioRecordBufferInBytes, bytesRead * 2, InetAddress.getByName(ip), port);
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
