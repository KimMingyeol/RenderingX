package constantin.renderingx.example.stepcounter;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Pair;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * Created by inseok on 4/3/17.
 */

public class StepCounter implements SensorEventListener {
    private MovingAverage mMovingAverager;
    private float mPrevAccelValue = 0.0f;
    private int mPrevAccelDirection = 0;
    private long mPrevAccelTimestamp = 0;
    private float[] mPrevNormGravVector = null; // normalized gravity unit vector

    private double mXTotalRotation = 0.0;
    private long mPrevGyroTimestamp = 0;
    private float mXRotationSign = 1.0f;   // default: counter-clockwise for counterclockwise landscape orientation
    public double getXRotationRad() {return mXTotalRotation;}

    private double mYTotalRotation = 0.0;   // 0 means straight forward. positive: upward
    public double getYRotationRad() {return mYTotalRotation;}

    private HandlerThread mSensingThread;
    private SensorManager mSensorManager;
    private Sensor mLinearSensor, mGyroSensor, mGravSensor;
    private ArrayList<Pair<StepListener, Handler>> mStepListeners     = new ArrayList<Pair<StepListener, Handler>>();
    private ArrayList<HeadingListenerInfo> mHeadingListeners = new ArrayList<HeadingListenerInfo>();


    public StepCounter(Context appContext, boolean isClockwise){
        mMovingAverager = new MovingAverage(Constants.MOVING_AVERAGE_LENGTH);
        mXRotationSign = (isClockwise)? -1.0f : +1.0f;

        mSensingThread = new HandlerThread("Sensing Thread");
        mSensingThread.start();
        Handler sensorHandler = new Handler(mSensingThread.getLooper());
        mSensorManager = (SensorManager) appContext.getSystemService(Context.SENSOR_SERVICE);
        mLinearSensor  = mSensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        mGyroSensor    = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        mGravSensor    = mSensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY);

        mSensorManager.registerListener(this, mLinearSensor,  Constants.ACCEL_DELAY_MICROSEC, sensorHandler);
        mSensorManager.registerListener(this, mGyroSensor  ,  Constants.GYRO_DELAY_MICROSEC, sensorHandler);
        mSensorManager.registerListener(this, mGravSensor  ,  Constants.GRAV_DELAY_MICROSEC, sensorHandler);
    }

    public void addStepListener(StepListener listener, Handler handler){
        mStepListeners.add(new Pair<StepListener, Handler>(listener, handler));
    }

    public void addHeadingListener(HeadingListener listener, Handler handler, double thresX, double thresY){
        synchronized (mHeadingListeners) {
            mHeadingListeners.add(new HeadingListenerInfo(listener, handler, thresX, thresY));
        }
    }

    public void removeHeadingListener(HeadingListener listener){
        synchronized (mHeadingListeners) {
            for (Iterator<HeadingListenerInfo> iter = mHeadingListeners.iterator(); iter.hasNext(); ) {
                HeadingListenerInfo info = iter.next();
                if (info.listener == listener) {
                    iter.remove();
                    break;
                }
            }
        }
    }

    private void notifyStepListeners(){
        for (Pair<StepListener, Handler> pair:mStepListeners){
            if (pair.second.getLooper().getThread().isAlive()) {
                final StepListener listener = pair.first;
                pair.second.post(new Runnable() {
                    @Override
                    public void run() {
                        listener.onStep();
                    }
                });
            }
        }
    }

    private void notifyHeadingListeners(){
        synchronized (mHeadingListeners) {
            for (HeadingListenerInfo info : mHeadingListeners){
                if (info.handler.getLooper().getThread().isAlive()) {
                    if (info.doesExceedThres(mXTotalRotation, mYTotalRotation)) {
                        final HeadingListener listener = info.listener;
                        info.handler.post(new Runnable() {
                            @Override
                            public void run() {
                                listener.onRotation(mXTotalRotation, mYTotalRotation);
                            }
                        });
                        info.lastAngleX = mXTotalRotation;
                        info.lastAngleY = mYTotalRotation;
                    }
                }
            }
        }
    }

    public void onSensorChanged(SensorEvent e){
        synchronized (this){
            float[] values = e.values.clone();
            Sensor sensor = e.sensor;
            long timestamp = e.timestamp;
            if (sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION){
                mMovingAverager.addSample(getVerticalAcceleration(values)); // interested in X-axis value
                float currValue = mMovingAverager.getAverage();
                int currDirection = (currValue - mPrevAccelValue > 0)? 1 : ( (currValue - mPrevAccelValue < 0)? -1 : 0 );
                long currTimestamp = timestamp;
                boolean isPositivePeak = (mPrevAccelDirection > 0 && currDirection <= 0);

                if (isPositivePeak && currValue >= Constants.STEP_ACCEL_PEAK
                        && (currTimestamp - mPrevAccelTimestamp) >= Constants.MINIMUM_STEP_PERIOD_NANOSECOND){
                    notifyStepListeners();
                    mPrevAccelTimestamp = currTimestamp;
                }
                mPrevAccelValue = currValue;
                mPrevAccelDirection = currDirection;
//                Log.w("StepCounter", "Y-axis angle: "+Math.toDegrees(mYTotalRotation));
            }else if (sensor.getType() == Sensor.TYPE_GYROSCOPE){
                float currValue = mXRotationSign * values[0]; // interested in x-axis rotation
                updateYaxisAngleRad();
                if (mPrevGyroTimestamp == 0){
                    mPrevGyroTimestamp = timestamp;
                }else{
                    double angleDisplacement = (double)currValue * ((double)(timestamp - mPrevGyroTimestamp))/1e9;
                    mXTotalRotation += angleDisplacement;
                    mPrevGyroTimestamp = timestamp;
                }
                notifyHeadingListeners();
            }else if (sensor.getType() == Sensor.TYPE_GRAVITY){
                if (mPrevNormGravVector == null){
                    mPrevNormGravVector = new float[3];
                }
                float magnitude = (float)Math.sqrt(values[0]*values[0] + values[1]*values[1] + values[2]*values[2]);
                mPrevNormGravVector[0] = values[0] / magnitude;
                mPrevNormGravVector[1] = values[1] / magnitude;
                mPrevNormGravVector[2] = values[2] / magnitude;
            }
        }   // end of synchronized(this)
    }

    private float getVerticalAcceleration(float[] accelVector){
        return dotProductWithUnitGravity(accelVector);
    }

    private float dotProductWithUnitGravity(float[] accelVector){
        float dotProd = accelVector[0]; // return original X-axis in case of no gravity vector available
        if (mPrevNormGravVector != null){ // make sure gravity vector has been fetched at least once
            dotProd = accelVector[0] * mPrevNormGravVector[0]
                    + accelVector[1] * mPrevNormGravVector[1]
                    + accelVector[2] * mPrevNormGravVector[2];
        }
        return dotProd;
    }

    private void updateYaxisAngleRad(){
        final float[] unitZaxisVector = {0.0f, 0.0f, 1.0f};
        mYTotalRotation = Math.acos((double)dotProductWithUnitGravity(unitZaxisVector)) - Math.PI/2;
    }

    public void onAccuracyChanged(Sensor s, int accuracy){

    }

    public void resetSteps(){
        // do something with the step counts
        notifyStepListeners();
    }

    public void resetHeading(){
        synchronized (this){
            mXTotalRotation = 0.0;
            notifyHeadingListeners();
        }
    }

    public void cleanup(){
        mSensorManager.unregisterListener(this);
        mSensingThread.quitSafely();

        for (Iterator<Pair<StepListener, Handler>> iter = mStepListeners.iterator(); iter.hasNext();){
            iter.next();
            iter.remove();
        }

        synchronized (mHeadingListeners) {
            for (Iterator<HeadingListenerInfo> iter = mHeadingListeners.iterator(); iter.hasNext();) {
                iter.next();
                iter.remove();
            }
        }
    }

    public interface HeadingListener {
        void onRotation(double angleX, double angleY);
    }

    public interface StepListener {
        void onStep();
    }

    class HeadingListenerInfo{
        public HeadingListener listener;
        public Handler handler;
        public double lastAngleX = 0;
        public double lastAngleY = 0;
        public double thresX, thresY;

        public HeadingListenerInfo(HeadingListener listener, Handler handler, double thresX, double thresY){
            this.listener = listener;
            this.handler  = (handler == null)? new Handler(mSensingThread.getLooper()) : handler;
            this.thresX = thresX;
            this.thresY = thresY;
        }

        public boolean doesExceedThres(double angleX, double angleY){
            return Math.abs(angleX - lastAngleX) > thresX || Math.abs(angleY - lastAngleY) > thresY;
        }
    }
}