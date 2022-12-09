package constantin.renderingx.example.d3_telepresence_android;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.Socket;

import org.json.JSONException;
import org.json.JSONObject;

import constantin.renderingx.example.stepcounter.Constants;
import constantin.renderingx.example.stepcounter.StepCounter;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;

/* From KimMingyeol/d3_telepresence_android repo. */
public class D3TelepresenceAndroid implements StepCounter.StepListener, StepCounter.HeadingListener {
    private StepCounter stepCounter;

    private boolean isStepping = false; // Shared (between two separate threads)
    private int consecutiveStop = 0; // Shared
    private boolean isAccessingShared = false;
    private final int stopThresh = 4;

    public InputStream inputStream;
    public OutputStream outputStream;
    private Socket socket = null;
    private String ip = ""; // D3 robot's IP
    private int port = -1;

    Thread threadReceive;
    Thread threadCommand;

    private double alpha0 = 3000;
    private double alpha = 3000;
    private double beta0 = 3000;
    private double beta = 3000;
    private double angle_thresh = 10;

    private double throt = 0;
    private double rot = 0;
    private int sendPerLoop = 4; // one loop: 200ms

    private double perceptual_x = 0;
    private double perceptual_y = 0;

    public D3TelepresenceAndroid(Context context){
        Handler uiHandler = new Handler(context.getMainLooper());
        stepCounter = new StepCounter(context, Constants.IS_POSITIVE_HEADING_CLOCKWISE);
        stepCounter.addStepListener(this, uiHandler);
        stepCounter.addHeadingListener(this, uiHandler, Math.toRadians(0.5), Double.POSITIVE_INFINITY);

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

    public void startSync() {
        threadCommand = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while(outputStream == null) {
                        Log.e("waiting for", "outputStream socket...");
                        threadCommand.sleep(1000);
                    }
//                    commandRetract();
//                    threadCommand.sleep(2000);
                    while (beta == 3000) {
                        Log.e("threadCommand Loop: ", "waiting for angle sync...");
                        threadCommand.sleep(100);
                    }
                    beta0 = beta; // beta: user's rotation state : We only care this right now

                    double dalpha = 0;
                    double dbeta = 0;
                    double rot_angle = 0;

                    throt = 0;
                    int loopNum = 0;
                    while(true) {
                        Log.d("threadCommand: ", "in Loop");
                        loopNum++;

                        if (Math.abs(beta - beta0) < 360 - Math.abs(beta - beta0)) {
                            dbeta = beta >= beta0 ? Math.abs(beta - beta0) : -Math.abs(beta - beta0);
                        } else {
                            dbeta = beta >= beta0 ? -(360 - Math.abs(beta - beta0)) : 360 - Math.abs(beta - beta0);
                        }

                        while(isAccessingShared) {
                            Log.d("threadCommand: ", "Waiting for Lock");
                        }
                        isAccessingShared = true;
                        consecutiveStop++;
                        if(consecutiveStop > stopThresh) {
                            isStepping = false;
                        }

                        rot = -dbeta;
                        if(isStepping) {
                            throt += 100; // mm scale
                        } else {
                            throt = 0;
                        }

                        Log.d("loopnum", String.valueOf(loopNum));

                        if(loopNum % sendPerLoop == 0) {
                            HttpPost(throt, rot);
                            throt = 0;
                        }

//                        throt = isStepping ? 0.5 : 0;
                        isAccessingShared = false;
//                        rot = Math.abs(rot_angle) > angle_thresh ? (rot_angle > 0 ? -1 : 1) : 0;
//                        commandNavigate(throt, rot); // one step = 30
                        threadCommand.sleep(200);
                    }
                } catch (InterruptedException e) {
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

    @SuppressLint("StaticFieldLeak")
    private void HttpPost(double r, double dtheta){
        new AsyncTask<Void, Void, JSONObject>(){
            @Override
            protected JSONObject doInBackground(Void... voids) {

                JSONObject result = null;
                try{
                    URL url = new URL("http://tbone.postech.ac.kr:20022/requestwarp/");
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestMethod("POST");
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    connection.setUseCaches(false);
                    connection.setConnectTimeout(15000);

                    OutputStream os = connection.getOutputStream();
                    String jsonString = "{\"r\": " + String.valueOf(r) + ", \"dtheta\": " + String.valueOf(dtheta) + "}";
                    os.write(jsonString.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                    os.close();

                    int responseCode = connection.getResponseCode();
                    if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                        String inputLine;
                        StringBuffer response = new StringBuffer();
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();

                    } else {
                        BufferedReader in = new BufferedReader(new InputStreamReader(connection.getErrorStream()));
                        String inputLine;
                        StringBuffer response = new StringBuffer();
                        while ((inputLine = in.readLine()) != null) {
                            response.append(inputLine);
                        }
                        in.close();
                        result = new JSONObject(response.toString());
                    }

                } catch (ConnectException e) {
                    e.printStackTrace();


                } catch (Exception e){
                    e.printStackTrace();
                }

                return result;
            }

            @Override
            protected void onPostExecute(JSONObject jsonObject) {
                super.onPostExecute(jsonObject);
            }

        }.execute();
    }
}
