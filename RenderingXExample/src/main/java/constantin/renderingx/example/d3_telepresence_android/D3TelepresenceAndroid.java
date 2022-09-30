package constantin.renderingx.example.d3_telepresence_android;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import constantin.renderingx.example.stepcounter.Constants;
import constantin.renderingx.example.stepcounter.StepCounter;

/* From KimMingyeol/d3_telepresence_android repo. */
public class D3TelepresenceAndroid implements StepCounter.StepListener, StepCounter.HeadingListener {
    private StepCounter stepCounter;

    private boolean isStepping = false; // Shared (between two separate threads)
    private int consecutiveStop = 0; // Shared
    private boolean isAccessingShared = false;
    private final int stopThresh = 3;

//    private SensorManager sensorManager;
//    private Sensor accel;

//    private float rotMatrix[];
//    private float orientation[];

    public InputStream inputStream;
    public OutputStream outputStream;
    private Socket socket = null;
    private String ip = ""; // D3 robot's IP
    private int port = 22023;

    Thread threadReceive;
    Thread threadCommand;

    private double alpha0 = 3000;
    private double alpha = 3000;
    private double beta0 = 3000;
    private double beta = 3000;
    private double angle_thresh = 10;

    private double throt = 0;
    private double rot = 0;

    public D3TelepresenceAndroid(Context context) {
        Handler uiHandler = new Handler(context.getMainLooper());
        stepCounter = new StepCounter(context, Constants.IS_POSITIVE_HEADING_CLOCKWISE);
        stepCounter.addStepListener(this, uiHandler);
        stepCounter.addHeadingListener(this, uiHandler, Math.toRadians(0.5), Double.POSITIVE_INFINITY);
/*
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accel = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if(accel != null) {
            sensorManager.registerListener(this, accel, 2500);
        } else {
            Log.e("error: ", "no rotation sensor");
        }
        rotMatrix = new float[16];
        orientation = new float[3];
*/
        threadReceive = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    socket = new Socket(ip, port);
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    while(inputStream == null) {
                        Log.e("waiting for", "inputStream socket...");
                        threadReceive.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                byte[] buffer = new byte[4096];
                String queued_str = "";
                int bytes;

                while(true) {
                    try { // 맨 처음 데이터가 json 중간부터 시작하는 데이터가 아니라는 가정 하에 짠 코드이다.
                        bytes = inputStream.read(buffer);
                        String rcvd = new String(buffer, 0, bytes);
                        String[] rcvd_arr = rcvd.split("\n");
                        JSONObject jsonObject;
                        JSONObject jsonObject_data;
                        JSONObject jsonObject_base;

                        for (int i=0; i<rcvd_arr.length; i++) {
//                            Log.e(String.valueOf(i) + ". Received from d3: ", rcvd_arr[i]);
                            queued_str += rcvd_arr[i];
                            try {
                                jsonObject = new JSONObject(queued_str);
                            } catch (JSONException e) {
//                                Log.e("JSON Conversion Failed!", "rcvd_arr[" + String.valueOf(i) + "] queued, and continue");
                                continue;
                            }
                            queued_str = "";
                            try {
                                jsonObject_data = jsonObject.getJSONObject("data");
                                jsonObject_base = jsonObject_data.getJSONObject("base");
                                alpha = jsonObject_base.getDouble("yaw") * (180/Math.PI);
                            } catch (JSONException e) {
                                Log.e("Something wrong...", "continue");
                                continue;
                            }
//                            Log.e("JSON Conversion Successful: ", jsonObject.toString());
                        }
//                        Log.e("***************** yaw: ", String.valueOf(json_yaw));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            }
        });
        threadReceive.start();
    }

    public void commandRetract() throws IOException {
        JSONObject jsonObject = new JSONObject();
        String jsonString = "";
        try {
            jsonObject.put("c", "base.kickstand.retract");
            jsonString = jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
//        Log.e("send: ", jsonString);
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

    public void commandSubscribe() throws IOException {
        String jsonString = "{"
                + "\"c\": \"events.subscribe\","
                + "\"d\": {"
                + "\"events\": ["
                + "\"DRPose.pose\""
                + "]"
                + "}"
                + "}";
//        Log.e("send: ", jsonString);
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

    public void commandResetOrigin() throws IOException {
        String jsonString = "{"
                + "\"c\": \"pose.resetOrigin\""
                + "}";
//        Log.e("send: ", jsonString);
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

    public void commandTurnBy(double deg) throws IOException {
        String jsonString = "{"
                + "\"c\": \"base.turnBy\","
                + "\"d\": {"
                + "\"degrees\": "
                + String.valueOf(deg)
                + ",\"degreesWhileDriving\": 0"
                + "}"
                + "}";
//        Log.e("send: ", jsonString);
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

    public void commandStop() throws IOException {
        commandNavigate(0, 0);
    }

    public void commandNavigate(double throttle, double turn) throws IOException {
        String jsonString = "{"
                + "\"c\": \"navigate.drive\","
                + "\"d\": {"
                + "\"throttle\": "
                + String.valueOf(throttle)
                + ",\"turn\": "
                + String.valueOf(turn)
                + "}"
                + "}";
        Log.e("send: ", jsonString);
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

/*
    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
//        double accel_x = sensorEvent.values[0];
//        double accel_y = sensorEvent.values[1];
//        double accel_z = sensorEvent.values[2];
//        Log.e("sensor type", String.valueOf(sensorEvent.sensor.getType()));
        if (sensorEvent.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
            SensorManager.getRotationMatrixFromVector(rotMatrix, sensorEvent.values);
            SensorManager.getOrientation(rotMatrix, orientation);
            beta = orientation[0] * (180 / Math.PI);
            double pitch = orientation[1] * (180 / Math.PI);
            double roll = orientation[2] * (180 / Math.PI);
            long timestamp = sensorEvent.timestamp;
            String values = String.format("(%4.0f, %4.0f, %4.0f)", beta, pitch, roll);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    public void stopSync() { //onStopped 이후에 Destroyed
        sensorManager.unregisterListener(this);
    }
*/

    public void startSync() {
//        if (toggleButton.isChecked()) {
//            sensorManager.registerListener(this, accel, 2500);
//        } else {
//            sensorManager.unregisterListener(this);
//        }
        threadCommand = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while(outputStream == null) {
                        Log.e("waiting for", "outputStream socket...");
                        threadCommand.sleep(1000);
                    }
                    commandRetract();
                    threadCommand.sleep(2000);
                    commandResetOrigin();
                    threadCommand.sleep(500);
                    commandSubscribe();

                    threadCommand.sleep(100);
                    while (alpha == 3000 || beta == 3000) {
                        Log.e("threadCommand Loop: ", "waiting for angle sync...");
                        threadCommand.sleep(100);
                    }

                    alpha0 = alpha;
                    beta0 = beta;

                    double dalpha = 0;
                    double dbeta = 0;
                    double rot_angle = 0;
                    while(true) {
                        if (Math.abs(beta - beta0) < 360 - Math.abs(beta - beta0)) {
                            dbeta = beta >= beta0 ? Math.abs(beta - beta0) : -Math.abs(beta - beta0);
                        } else {
                            dbeta = beta >= beta0 ? -(360 - Math.abs(beta - beta0)) : 360 - Math.abs(beta - beta0);
                        }

                        if (Math.abs(alpha - alpha0) < 360 - Math.abs(alpha - alpha0)) {
                            dalpha = alpha >= alpha0 ? Math.abs(alpha - alpha0) : -Math.abs(alpha - alpha0);
                        } else {
                            dalpha = alpha >= alpha0 ? -(360 - Math.abs(alpha - alpha0)) : 360 - Math.abs(alpha - alpha0);
                        }

                        if (Math.abs(dbeta - dalpha) < 360 - Math.abs(dbeta - dalpha)) {
                            rot_angle = dbeta >= dalpha ? Math.abs(dbeta - dalpha) : -Math.abs(dbeta - dalpha);
                        } else {
                            rot_angle = dbeta >= dalpha ? -(360 - Math.abs(dbeta - dalpha)) : 360 - Math.abs(dbeta - dalpha);
                        }

                        while(isAccessingShared) {
                            Log.d("threadCommand: ", "Waiting for Lock");
                        }
                        isAccessingShared = true;
                        consecutiveStop++;
                        if(consecutiveStop > stopThresh) {
                            isStepping = false;
                        }
                        throt = isStepping ? 0.5 : 0;
                        isAccessingShared = false;
//                        rot = Math.abs(rot_angle) > angle_thresh ? (rot_angle > 0 ? -Math.min(1, rot_angle * Math.PI / 180) : Math.min(1, -rot_angle * Math.PI / 180)) : 0;
                        rot = Math.abs(rot_angle) > angle_thresh ? (rot_angle > 0 ? -1 : 1) : 0;
//                        Log.e("Step update: ", String.valueOf(currStep) + " vs " + String.valueOf(prevStep));
                        commandNavigate(throt, rot);
//                        if (Math.abs(rot_angle) > angle_thresh) {
////                            commandTurnBy(rot_angle); // 속도 제어가 불가능한 것으로 보임
//                            Log.e("rot_angle: ", String.valueOf(rot_angle * Math.PI / 180) + ", dbeta: " + String.valueOf(dbeta) + ", dalpha: " + String.valueOf(dalpha) + ", alpha: " + String.valueOf(alpha) + ", alpha0: " + String.valueOf(alpha0));
//                            commandNavigate(0, rot_angle > 0 ? -Math.min(1, rot_angle * Math.PI / 180) : Math.min(1, -rot_angle * Math.PI / 180));
//                        }
                        threadCommand.sleep(200);
                    }
                } catch (IOException | InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
        threadCommand.start();
    }

    // StepCounter.StepListener
    @Override
    public void onStep() {
        while(isAccessingShared) {
            Log.d("onStep: ", "Waiting for Lock");
        }
        isAccessingShared = true;
        isStepping = true;
        consecutiveStop = 0;
        isAccessingShared = false;
    }
    // StepCounter.HeadingListener
    @Override
    public void onRotation(double angleX, double angleY) {
        beta = angleX * (180 / Math.PI);
    }
}
