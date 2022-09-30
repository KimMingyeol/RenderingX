package constantin.renderingx.example.stepcounter;

/**
 * Created by inseok on 5/13/17.
 */

public class Constants {
    public static final int SOCKET_CLOSED    = 0;
    public static final int SOCKET_ACCEPTING = 1;
    public static final int SOCKET_CONNECTED = 2;

    public final static int MOVING_AVERAGE_LENGTH = 3;
    public final static float STEP_ACCEL_PEAK = 1.0f;
    public final static int ACCEL_DELAY_MICROSEC = 10000;
    public final static int GYRO_DELAY_MICROSEC  = 10000;
    public final static int GRAV_DELAY_MICROSEC  = 10000;

    public final static long MINIMUM_STEP_PERIOD_NANOSECOND = (long)0.3e9;
    public final static double MINIMUM_ROTATION_TO_SEND_CMD = Math.toRadians(2);

    public final static double SINGLE_STEP_DISTANCE = 0.5;

    public final static boolean IS_POSITIVE_HEADING_CLOCKWISE = false;

    public final static String CMD_SINGLE_FORWARD = "f";
    public final static String CMD_SINGLE_LEFT_TURN = "l";
    public final static String CMD_SINGLE_RIGHT_TURN = "r";
    public final static String CMD_SINGLE_BACKWARD = "b";

    public final static int FRAME_SIZE_BYTE_LENGTH = 4;

    public static double REMOTE_CAMERA_FOV_VERTICAL = Math.toRadians(54.4);  // this is for iPad Prop 9.7" 640x480 front-camera
    public final static double REMOTE_CAMERA_FOV_VERTICAL_MIN = Math.toRadians(54.4);
    public final static double REMOTE_CAMERA_FOV_VERTICAL_MAX = Math.toRadians(54.4/0.67);
    public static void setRemoteFoVY(double angle){REMOTE_CAMERA_FOV_VERTICAL = angle;}

    public static final double REMOTE_SPEED_EMPIRICAL_MAX = 0.35;     // meter per second, actual speed may be faster
    public static final double REMOTE_SPEED_HYPOTHETICAL_MAX = 0.50;    // meter per second, actual speed may be faster
    public static final double REMOTE_SPEED_NOISE_MAX = 0.04;   // noise speed even when commanded to stop

    public final static float BINOCULAR_SPACING_RATIO = 0.05f; // 5% of the screen width
    public final static double MIN_ANGLE_DIFF_TO_SHOW_ARROW = Math.toRadians(5.0);

    public final static long MIN_FRAME_INTERVAL_MILLIS = 30;
    public final static int MAX_FRAME_INTERVAL_MILLIS = 250;
}