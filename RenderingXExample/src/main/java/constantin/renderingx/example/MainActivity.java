package constantin.renderingx.example;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import android.util.Base64;
import android.widget.EditText;

import com.google.android.material.textfield.TextInputEditText;

import constantin.renderingx.core.deviceinfo.AWriteGLESInfo;
import constantin.renderingx.core.deviceinfo.Extensions;
import constantin.renderingx.core.vrsettings.FSettingsVR;
import constantin.renderingx.example.mono.AExampleRendering;
import constantin.renderingx.example.stereo.distortion.AExampleDistortion;
import constantin.renderingx.example.stereo.video360degree.AExample360Video;
import constantin.renderingx.example.supersync.AExampleSuperSync;
import constantin.helper.RequestPermissionHelper;

public class MainActivity extends AppCompatActivity {
    private Context context;

    private TextInputEditText double3IPText;
    private TextInputEditText double3PortText;
    private TextInputEditText serverIPText;
    private TextInputEditText serverPortText;
    private Button uploadButton;

    private File faceCaptureDirectory;
    private String cameraCaptureFileName;
    private ActivityResultLauncher<Intent> faceCameraActivityResultLauncher;

    private Socket faceCaptureUploadSocket = null;
    private InputStream faceCaptureUploadInputStream = null;
    private OutputStream faceCaptureUploadOutputStream = null;

    private String double3IP = "";
    private int double3Port = -1;
    private String serverIP = "";
    private int serverPort = -1;
    private boolean isServerReadyToReceive;

    private String base64FaceCaptureString;

    private final RequestPermissionHelper requestPermissionHelper=new RequestPermissionHelper(new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CAMERA
    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context=this;
        setContentView(R.layout.activity_sofar);
        //This retrieves any HW info needed for the app
        AWriteGLESInfo.writeGLESInfoIfNeeded(this);

        faceCaptureDirectory = new File(getFilesDir().getAbsolutePath().concat("/photos/"));
        if (!faceCaptureDirectory.exists())
            faceCaptureDirectory.mkdirs();
        cameraCaptureFileName = "neutralFace";

        double3IPText = findViewById(R.id.Double3IPText);
        double3PortText = findViewById(R.id.Double3PortText);
        serverIPText = findViewById(R.id.ServerIPText);
        serverPortText = findViewById(R.id.ServerPortText);

        uploadButton = findViewById(R.id.UploadButton);
        uploadButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                File faceCaptureFile = new File(faceCaptureDirectory, cameraCaptureFileName);
                Intent faceCaptureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                faceCaptureIntent.putExtra(MediaStore.EXTRA_OUTPUT, FileProvider.getUriForFile(context, "constantin.renderingx.example.fileprovider", faceCaptureFile));
                if (faceCaptureIntent.resolveActivity(getPackageManager()) != null)
                    faceCameraActivityResultLauncher.launch(faceCaptureIntent);
            }
        });

        faceCameraActivityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                try {
                    File faceCaptureFile = new File(faceCaptureDirectory, cameraCaptureFileName);
                    String faceCaptureFileAbsolutePath = faceCaptureFile.getAbsolutePath();
                    Bitmap faceCaptureBitmap = rotateCameraCaptureBitmap(BitmapFactory.decodeFile(faceCaptureFileAbsolutePath),
                            new ExifInterface(faceCaptureFileAbsolutePath));
                    faceCaptureFile.delete();

                    ByteArrayOutputStream faceCaptureOutputStream = new ByteArrayOutputStream();
                    faceCaptureBitmap.compress(Bitmap.CompressFormat.JPEG, 50, faceCaptureOutputStream);
                    base64FaceCaptureString = Base64.encodeToString(faceCaptureOutputStream.toByteArray(), Base64.NO_WRAP);

                    isServerReadyToReceive = false;

                    new Thread(() -> { // Server-side Thread + Functional Style :)
                        byte[] socketReadBuffer = new byte[4096];
                        int numOfBytes;
                        try {
                            if (faceCaptureUploadSocket == null) {
                                setAddressByTextInputEditText();
                                faceCaptureUploadSocket = new Socket(serverIP, serverPort);
                                faceCaptureUploadInputStream = faceCaptureUploadSocket.getInputStream();
                                faceCaptureUploadOutputStream = faceCaptureUploadSocket.getOutputStream();
                            }

                            while(true) {
                                numOfBytes = faceCaptureUploadInputStream.read(socketReadBuffer);
                                if (numOfBytes > 0) {
                                    String responseFromSocket = new String(socketReadBuffer, 0, numOfBytes);
                                    if (responseFromSocket.equals("OK")) {
                                        Log.d("Capture Upload", "OK Received from the server");
                                        isServerReadyToReceive = true;
                                        break;
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();

                    new Thread(() -> {
                        try {
                            while(faceCaptureUploadOutputStream == null)
                                Log.d("Waiting for", "faceCapture Upload OutputStream 0");

                            int lenOfBase64FaceCaptureString = base64FaceCaptureString.length();
                            Log.d("String Size", String.valueOf(lenOfBase64FaceCaptureString));
                            faceCaptureUploadOutputStream.write(String.format("?Size:%d", lenOfBase64FaceCaptureString).getBytes());

                            /// send faceCaptureBytes
                            while(!isServerReadyToReceive)
                                Log.d("Waiting for", "faceCapture Upload OutputStream 1");

                            byte[] faceCaptureBytes = base64FaceCaptureString.getBytes();
                            faceCaptureUploadOutputStream.write(faceCaptureBytes);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }).start();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        findViewById(R.id.ConnectButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final Intent intent = new Intent().setClass(context, AExample360Video.class);
                intent.putExtra(AExample360Video.KEY_SPHERE_MODE, AExample360Video.SPHERE_MODE_GVR_EQUIRECTANGULAR);
                setAddressByTextInputEditText();
                intent.putExtra("Double3 IP", double3IP);
                intent.putExtra("Double3 Port", double3Port);
                intent.putExtra("Server IP", serverIP);
                intent.putExtra("Server Port", serverPort);
                startActivity(intent);
            }
        });

        requestPermissionHelper.checkAndRequestPermissions(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        requestPermissionHelper.onRequestPermissionsResult(requestCode,permissions,grantResults);
    }

    public boolean isSuperSyncSupported(){
        return FSettingsVR.isSuperSyncSupported(context);
    }

    public Bitmap rotateCameraCaptureBitmap(Bitmap faceCaptureBitmap, ExifInterface faceCaptureExifInterface) {
        int faceCaptureOrientation = faceCaptureExifInterface.getAttributeInt(faceCaptureExifInterface.TAG_ORIENTATION, faceCaptureExifInterface.ORIENTATION_NORMAL);

        int faceCaptureOrientationDegree = 0;
        if (faceCaptureOrientation == ExifInterface.ORIENTATION_ROTATE_90)
            faceCaptureOrientationDegree = 90;
        else if (faceCaptureOrientation == ExifInterface.ORIENTATION_ROTATE_180)
            faceCaptureOrientationDegree = 180;
        else if (faceCaptureOrientation == ExifInterface.ORIENTATION_ROTATE_270)
            faceCaptureOrientationDegree = 270;

        Matrix rotationMatrix = new Matrix();
        rotationMatrix.setRotate(faceCaptureOrientationDegree, (float) faceCaptureBitmap.getWidth()/2, (float) faceCaptureBitmap.getHeight()/2);
        Bitmap rotatedFaceCaptureBitmap = Bitmap.createBitmap(faceCaptureBitmap, 0, 0, faceCaptureBitmap.getWidth(), faceCaptureBitmap.getHeight(), rotationMatrix, true);

        if (faceCaptureBitmap != rotatedFaceCaptureBitmap) {
            faceCaptureBitmap.recycle();
            faceCaptureBitmap = rotatedFaceCaptureBitmap;
        }

        return faceCaptureBitmap;
    }

    private void setAddressByTextInputEditText() {
        double3IP = double3IPText.getText().toString();
        double3Port = Integer.parseInt(double3PortText.getText().toString());
        serverIP = serverIPText.getText().toString();
        serverPort = Integer.parseInt(serverPortText.getText().toString());
    }

    // TODO: stop connection!
    private void closeUploadSocket() {
        if (faceCaptureUploadSocket == null)
            return;

        try {
            faceCaptureUploadSocket.close();
            faceCaptureUploadInputStream.close();
            faceCaptureUploadOutputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
