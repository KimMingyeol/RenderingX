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
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;

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
    private final int stopThresh = 2;

    private final double maxTiltAngle = 26;
    private final double minTiltAngle = -21.5;
    private double headTiltPercentage = -10;

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

    public D3TelepresenceAndroid(Context context, String ip, int port) {
        Handler uiHandler = new Handler(context.getMainLooper());
        this.ip = ip;
        this.port = port;

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
                        Log.d("waiting for", "inputStream socket...");
                        threadReceive.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                byte[] buffer = new byte[4096];
                String queued_str = "";
                int bytes;

                while(true) {
                    try { // This code assumes that the first incoming data always starts from the beginning of JSON data.
                        bytes = inputStream.read(buffer);
                        String rcvd = new String(buffer, 0, bytes);
                        String[] rcvd_arr = rcvd.split("\n");
                        JSONObject jsonObject;
                        JSONObject jsonObject_data;
                        JSONObject jsonObject_base;

                        for (int i=0; i<rcvd_arr.length; i++) {
                            queued_str += rcvd_arr[i];
                            try {
                                jsonObject = new JSONObject(queued_str);
                            } catch (JSONException e) {
                                continue;
                            }
                            queued_str = "";
                            try {
                                jsonObject_data = jsonObject.getJSONObject("data");
                                jsonObject_base = jsonObject_data.getJSONObject("base");
                                alpha = jsonObject_base.getDouble("yaw") * (180/Math.PI);
                            } catch (JSONException e) {
                                Log.d("Something wrong...", "continue");
                                continue;
                            }
                        }
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
//        Log.d("send: ", jsonString);
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
//        Log.d("send: ", jsonString);
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

    public void commandResetOrigin() throws IOException {
        String jsonString = "{"
                + "\"c\": \"pose.resetOrigin\""
                + "}";
//        Log.d("send: ", jsonString);
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
//        Log.d("send: ", jsonString);
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
//        Log.d("send: ", jsonString);
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

    public void commandTiltMove(double speed) throws IOException {
        String jsonString = "{"
                + "\"c\": \"tilt.move\","
                + "\"d\": {"
                + "\"speed\": "
                + String.valueOf(speed)
                + "}"
                + "}";
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

    public void commandTiltTarget(double percent) throws IOException {
        String jsonString = "{"
                + "\"c\": \"tilt.target\","
                + "\"d\": {"
                + "\"percent\": "
                + String.valueOf(percent)
                + "}"
                + "}";
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

    public void commandTiltMinLimitDisable() throws IOException {
        String jsonString = "{"
                + "\"c\": \"tilt.minLimit.disable\""
                + "}";
        byte[] cmd = jsonString.getBytes();
        outputStream.write(cmd);
    }

    public double percentageToTiltAngle(double percentage) {
        assert(percentage >=0 && percentage <= 1);
        return minTiltAngle * percentage + maxTiltAngle * (1-percentage);
    }

    public double tiltAngleToPercentage(double tiltAngle){
        if(tiltAngle <= minTiltAngle) {
            return 1;
        } else if(tiltAngle >= maxTiltAngle) {
            return 0;
        } else {
            return (maxTiltAngle - tiltAngle)/(maxTiltAngle - minTiltAngle);
        }
    }

    public void startSync() {
        threadCommand = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while(outputStream == null) {
                        Log.d("waiting for", "outputStream socket...");
                        threadCommand.sleep(1000);
                    }

                    commandTiltMinLimitDisable();
                    threadCommand.sleep(100);
                    commandTiltMove(0.75);
                    threadCommand.sleep(100);
                    commandTiltMove(0);
                    threadCommand.sleep(100);
                    commandTiltTarget(tiltAngleToPercentage(0));
                    threadCommand.sleep(100);

                    commandRetract();
                    threadCommand.sleep(2000);

                    commandResetOrigin();
                    threadCommand.sleep(500);
                    commandSubscribe();
                    threadCommand.sleep(100);

                    while (alpha == 3000 || beta == 3000 || headTiltPercentage == -10) {
                        Log.d("threadCommand Loop: ", "waiting for angle sync...");
                        threadCommand.sleep(100);
                    }

                    HttpPost("http://141.223.208.180:22042/initiate_movement/", -1, -1);
                    Log.d("Webrequest", "initiate_movement sent");

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
                        throt = isStepping ? 0.75 : 0;
                        isAccessingShared = false;

                        rot = Math.abs(rot_angle) > angle_thresh ? (rot_angle > 0 ? -1 : 1) : 0;

//                        commandNavigate(throt, rot);
                        HttpPost("http://141.223.208.180:22042/movement/", throt, rot);
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
            Log.d("onStep: ", "Waiting for Lock"); // This lock may be needed for other resources, like headTiltPercentage... IDK..
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
        headTiltPercentage = tiltAngleToPercentage(angleY * (180 / Math.PI));
    }

    //TODO: Implement & Call stopSync on disconnection
    private void closeD3Socket() {
        if (socket == null)
            return;

        try {
            socket.close();
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @SuppressLint("StaticFieldLeak")
    private void HttpPost(String postURL, double throttle, double turn){
        new AsyncTask<Void, Void, JSONObject>(){
            @Override
            protected JSONObject doInBackground(Void... voids) {
                Log.d("HttpPost", "sending");
                JSONObject result = null;
                try{
                    URL url = new URL(postURL);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();

                    connection.setRequestProperty("Accept", "application/json");
                    connection.setRequestProperty("Content-Type", "application/json");
                    connection.setRequestMethod("POST");
                    connection.setDoInput(true);
                    connection.setDoOutput(true);
                    connection.setUseCaches(false);
                    connection.setConnectTimeout(20000);

                    OutputStream os = connection.getOutputStream();
                    String jsonString = "{\"throttle\": " + throttle + ", \"turn\": " + turn + "}";
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
