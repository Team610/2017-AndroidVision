package maaran;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.imgproc.Imgproc;
import shooterVision.R;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.SurfaceView;


import org.florescu.android.rangeseekbar.RangeSeekBar;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;


public class ColorBlobDetectionActivity extends Activity implements CvCameraViewListener2, RangeSeekBar.OnRangeSeekBarChangeListener, SensorEventListener{
    private static final String  TAG = "OCV::Activity"; //Filter by this tag to see specific printouts

    private SensorManager senSensorManager;
    private Sensor senAccelerometer;

    private long lastUpdate = 0;
    private float last_x, last_y, last_z;

    private float[] gravity;
    private float[] acceleration;

    private float offset;

    private double[] yMedian, xMedian;
    private int medianCounter;

    private double area;
    private double oldArea;

    private boolean predictiveEnabled;
    private static final int port = 5800;
    public final String host = "localhost";
    private final String TAGOne = "Communication";
    String xCenter;

    private double width;

    private String message;

    private Socket socket;

    private PredictiveTargeting prediction;

    private Mat rgbaColors; //frame
    private ColorBlobDetector colorDetector;
    private Scalar contourColor;
    private ViewGroup sliderView;
    private ViewGroup presetView;

    private RangeSeekBar hSlider;
    private RangeSeekBar sSlider;
    private RangeSeekBar vSlider;

    private  boolean cameraCreated;

    Rect rect;
    Rect oldRect;
    Point topL;
    Point botR;

    private Scalar upperLimit = new Scalar(0);
    private Scalar lowerLimit = new Scalar(0);

    private View option;
    private View gameView;
    Client myClient;

    private Rect roi = new Rect(0,0,300,480);

    private boolean sliderShow = false;
    private boolean gameMode = false;

    private CameraBridgeViewBase cvCameraView;

    private double xCentroid = 0;
    private double yCentroid = 0;

    static boolean clientRun = true;

    private BaseLoaderCallback  mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.i(TAG, "OpenCV loaded successfully");
                    cvCameraView.enableView();
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };

    public ColorBlobDetectionActivity() {
        Log.i(TAG, "Instantiated new " + this.getClass());
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        //Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        cameraCreated = false;
        oldArea = 0;
        area = 0;
        medianCounter = 1;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);
        sliderView = (ViewGroup) findViewById(R.id.sliders);
        option = (View) findViewById(R.id.options);
        presetView = (ViewGroup) findViewById(R.id.presets);

        senSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        senAccelerometer = senSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        yMedian = new double[3];
        xMedian = new double[3];

        hSlider = (RangeSeekBar) findViewById(R.id.hSlider);
        hSlider.setOnRangeSeekBarChangeListener(this);
        sSlider = (RangeSeekBar) findViewById(R.id.sSlider);
        sSlider.setOnRangeSeekBarChangeListener(this);
        vSlider = (RangeSeekBar) findViewById(R.id.vSlider);
        vSlider.setOnRangeSeekBarChangeListener(this);

        cvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        cvCameraView.setMaxFrameSize(640,480); //change this to any valid camera resolution
        cvCameraView.setKeepScreenOn(true);
        cvCameraView.enableFpsMeter();
        cvCameraView.setVisibility(SurfaceView.VISIBLE);
        cvCameraView.setCvCameraViewListener(this);
        option.bringToFront();

        predictiveEnabled = false;
        gravity = new float[3];
        acceleration = new float[3];
        rect = new Rect();
        oldRect = new Rect();
        topL = new Point();
        botR = new Point();
        socket = null;
        myClient = new Client();
        myClient.execute(socket);

        prediction = new PredictiveTargeting();
        offset = 0;
    }

    @Override
    public void onPause() {
        super.onDestroy();
        super.onPause();
        senSensorManager.unregisterListener((SensorEventListener) this);
        if (cvCameraView != null)
            cvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
        senSensorManager.registerListener((SensorEventListener)this,senAccelerometer,SensorManager.SENSOR_DELAY_NORMAL);
        if (!OpenCVLoader.initDebug()) {
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, this, mLoaderCallback);
        } else {
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }
    }

    public void onDestroy() {
        super.onDestroy();
        if (cvCameraView != null)
            cvCameraView.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
        rgbaColors = new Mat(height, width, CvType.CV_8UC4);
        contourColor = new Scalar(255,0,0,255);
        colorDetector = new ColorBlobDetector();
        colorDetector.setupHSV();
        loadData();
        cameraCreated = true;
        loadData();
    }

    public void onCameraViewStopped() {
        rgbaColors.release ();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        message = null;
        rgbaColors = inputFrame.rgba(); //takes rgba frame (a is just for opacity)
        Mat areaToTrack = new Mat(rgbaColors,roi);
        width = rgbaColors.height();
        colorDetector.process(areaToTrack);
        if(!sliderShow) {
            if (colorDetector.bestContour()) { //if there is a contour worth tracking
                rect = colorDetector.getRect();
                oldRect = colorDetector.oldRect;
                if(rect.area() < oldRect.area() - 500 || rect.area() > oldRect.area() + 500)
                    rect = oldRect;
                topL = new Point(rect.x, rect.y);
                botR = new Point(rect.x + rect.width, rect.y + rect.height);
                xCentroid = rect.y + (rect.height/2);
                yCentroid = rect.x + (rect.width/2);
                xMedian[medianCounter%3] = xCentroid;
                yMedian[medianCounter%3] = yCentroid;
                medianCounter++;
                Arrays.sort(yMedian);
                Arrays.sort(xMedian);

                if(colorDetector.trackingCounter == 0) {
                    yCentroid = yMedian[1];
                    xCentroid = xMedian[1];
                }
                else if(colorDetector.trackingCounter == 1) {
                    yCentroid = (yMedian[1] + yMedian[2]) / 2;
                    xCentroid = (xMedian[1] + xMedian[2]) / 2;
                }
                else {
                    yCentroid = yMedian[2];
                    xCentroid = xMedian[2];
                }

                xCentroid -= width/2;
                xCenter = xCentroid + "&"+ yCentroid + "/n";
                Log.d("xCentroid", xCenter);
                messageToSend.message = xCenter;
                Imgproc.rectangle(rgbaColors, topL, botR, contourColor, 4); //draws rectangle over the current frame before returning it
                Imgproc.circle(rgbaColors,new Point(yCentroid,xCentroid += width/2),5,contourColor);
            }
            else if(colorDetector.isBall) {
                rect = colorDetector.oldRect;
                topL = new Point(rect.x, rect.y);
                botR = new Point(rect.x + rect.width, rect.y + rect.height);
                xCentroid = rect.y + (rect.height/2);
                yCentroid = rect.x + (rect.width/2);
                xMedian[medianCounter%3] = xCentroid;
                yMedian[medianCounter%3] = yCentroid;
                medianCounter++;
                Arrays.sort(yMedian);
                Arrays.sort(xMedian);

                if(colorDetector.trackingCounter == 0) {
                    yCentroid = yMedian[1];
                    xCentroid = xMedian[1];
                }
                else if(colorDetector.trackingCounter == 1) {
                    yCentroid = (yMedian[1] + yMedian[2]) / 2;
                    xCentroid = (xMedian[1] + xMedian[2]) / 2;
                }
                else {
                    yCentroid = yMedian[2];
                    xCentroid = xMedian[2];
                }

                xCentroid -= width/2;
                xCenter = xCentroid + "&"+ yCentroid + "/n";
                Log.d("xCentroid", xCenter);
                messageToSend.message = xCenter;
                Imgproc.rectangle(rgbaColors, topL, botR, contourColor, 4); //draws rectangle over the current frame before returning it
                Imgproc.circle(rgbaColors,new Point(yCentroid,xCentroid += width/2),5,contourColor);
            }
            else
                messageToSend.message = "No target&No target/n";
        }
        else{
            rgbaColors = colorDetector.maskedFrame(rgbaColors);
        }
        return rgbaColors;
    }

    public void selfDestruct(View view){
        if(!sliderShow) {
            sliderView.bringToFront();
            sliderShow = true;
            presetView.bringToFront();
        }
        else if(sliderShow){
            cvCameraView.bringToFront();
            sliderShow = false;
            saveData();
        }
        option.bringToFront();
    }

    public void bluePreset(View view){
        lowerLimit.val[0] = 109;
        lowerLimit.val[1] = 99;
        lowerLimit.val[2] = 0;
        upperLimit.val[0] = 180;
        upperLimit.val[1] = 250;
        upperLimit.val[2] = 113;

        hSlider.setSelectedMinValue(lowerLimit.val[0]);
        sSlider.setSelectedMinValue(lowerLimit.val[1]);
        vSlider.setSelectedMinValue(lowerLimit.val[2]);
        hSlider.setSelectedMaxValue(upperLimit.val[0]);
        sSlider.setSelectedMaxValue(upperLimit.val[1]);
        vSlider.setSelectedMaxValue(upperLimit.val[2]);

        colorDetector.setHSV(lowerLimit,upperLimit);
    }

    public void greenPreset(View view){
        lowerLimit.val[0] = 0;
        lowerLimit.val[1] = 248;
        lowerLimit.val[2] = 0;
        upperLimit.val[0] = 5;
        upperLimit.val[1] = 255;
        upperLimit.val[2] = 5;

        hSlider.setSelectedMinValue(lowerLimit.val[0]);
        sSlider.setSelectedMinValue(lowerLimit.val[1]);
        vSlider.setSelectedMinValue(lowerLimit.val[2]);
        hSlider.setSelectedMaxValue(upperLimit.val[0]);
        sSlider.setSelectedMaxValue(upperLimit.val[1]);
        vSlider.setSelectedMaxValue(upperLimit.val[2]);

        colorDetector.setHSV(lowerLimit,upperLimit);
    }

    public void redPreset(View view){
        lowerLimit.val[0] = 50;
        lowerLimit.val[1] = 50;
        lowerLimit.val[2] = 50;
        upperLimit.val[0] = 245;
        upperLimit.val[1] = 245;
        upperLimit.val[2] = 245;

        hSlider.setSelectedMinValue(lowerLimit.val[0]);
        sSlider.setSelectedMinValue(lowerLimit.val[1]);
        vSlider.setSelectedMinValue(lowerLimit.val[2]);
        hSlider.setSelectedMaxValue(upperLimit.val[0]);
        sSlider.setSelectedMaxValue(upperLimit.val[1]);
        vSlider.setSelectedMaxValue(upperLimit.val[2]);

        colorDetector.setHSV(lowerLimit,upperLimit);
    }

    public void enablePredictive(View view){
        if(predictiveEnabled)
            predictiveEnabled = false;
        else
            predictiveEnabled = true;
    }

    public void retroPreset(View view){
        lowerLimit.val[0] = 22;
        lowerLimit.val[1] = 151;
        lowerLimit.val[2] = 149;
        upperLimit.val[0] = 96;
        upperLimit.val[1] = 255;
        upperLimit.val[2] = 255;

        hSlider.setSelectedMinValue(lowerLimit.val[0]);
        sSlider.setSelectedMinValue(lowerLimit.val[1]);
        vSlider.setSelectedMinValue(lowerLimit.val[2]);
        hSlider.setSelectedMaxValue(upperLimit.val[0]);
        sSlider.setSelectedMaxValue(upperLimit.val[1]);
        vSlider.setSelectedMaxValue(upperLimit.val[2]);

        colorDetector.setHSV(lowerLimit,upperLimit);
    }

    @Override
    public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Object minValue, Object maxValue) {
        //Log.d("Barber"," "+ bar.getId());
        if(bar.getId() == hSlider.getId()) { //hbar id
            upperLimit.val[0] = bar.getSelectedMaxValue().doubleValue();
            lowerLimit.val[0] = bar.getSelectedMinValue().doubleValue();
        }
        if(bar.getId() == sSlider.getId()) { //sbar id
            upperLimit.val[1] = bar.getSelectedMaxValue().doubleValue();
            lowerLimit.val[1] = bar.getSelectedMinValue().doubleValue();
        }
        if(bar.getId() == vSlider.getId()) { //vbar id
            upperLimit.val[2] = bar.getSelectedMaxValue().doubleValue();
            lowerLimit.val[2] = bar.getSelectedMinValue().doubleValue();
        }
        colorDetector.setHSV(lowerLimit,upperLimit);
    }

    private void saveData(){ //not working properly
        SharedPreferences sp = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        editor.putInt("lowerH",(int)lowerLimit.val[0]);
        editor.putInt("lowerS",(int)lowerLimit.val[1]);
        editor.putInt("lowerV", (int)lowerLimit.val[2]);

        editor.putInt("upperH",(int)upperLimit.val[0]);
        editor.putInt("upperS", (int)upperLimit.val[1]);
        editor.putInt("upperV", (int)upperLimit.val[2]);
        editor.commit();
        Log.d("Ss", "" + upperLimit.val[0]);
    }

    private void loadData(){ //not working properly
        Log.d("Ss", "Color initialized");
        if(cameraCreated) {
            SharedPreferences sp = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
            lowerLimit.val[0] = sp.getInt("lowerH", (int)lowerLimit.val[0]);
            lowerLimit.val[1] = sp.getInt("lowerS", (int)lowerLimit.val[1]);
            lowerLimit.val[2] = sp.getInt("lowerV", (int)lowerLimit.val[2]);


            upperLimit.val[0] = sp.getInt("upperH", (int)upperLimit.val[0]);
            upperLimit.val[1] = sp.getInt("upperS", (int)upperLimit.val[1]);
            upperLimit.val[2] = sp.getInt("upperV", (int)upperLimit.val[2]);

            //if(lowerLimit.val[0] > 0 && lowerLimit.val[1]>0 && lowerLimit.val[2]>0){
            hSlider.setSelectedMinValue(lowerLimit.val[0]);
            sSlider.setSelectedMinValue(lowerLimit.val[1]);
            vSlider.setSelectedMinValue(lowerLimit.val[2]);
            //}
            //if(upperLimit.val[0]>0 && upperLimit.val[1] > 0 && upperLimit.val[2] > 0){
            hSlider.setSelectedMaxValue(upperLimit.val[0]);
            sSlider.setSelectedMaxValue(upperLimit.val[1]);
            vSlider.setSelectedMaxValue(upperLimit.val[2]);
            colorDetector.setHSV(lowerLimit, upperLimit);
            Log.d("Ss", "" + upperLimit.val[0]);
        }
        else{
            SharedPreferences sp = getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
            lowerLimit.val[0] = sp.getInt("lowerH", (int)lowerLimit.val[0]);
            lowerLimit.val[1] = sp.getInt("lowerS", (int)lowerLimit.val[1]);
            lowerLimit.val[2] = sp.getInt("lowerV", (int)lowerLimit.val[2]);


            upperLimit.val[0] = sp.getInt("upperH", (int)upperLimit.val[0]);
            upperLimit.val[1] = sp.getInt("upperS", (int)upperLimit.val[1]);
            upperLimit.val[2] = sp.getInt("upperV", (int)upperLimit.val[2]);

            //if(lowerLimit.val[0] > 0 && lowerLimit.val[1]>0 && lowerLimit.val[2]>0){
            hSlider.setSelectedMinValue(lowerLimit.val[0]);
            sSlider.setSelectedMinValue(lowerLimit.val[1]);
            vSlider.setSelectedMinValue(lowerLimit.val[2]);
            //}
            //if(upperLimit.val[0]>0 && upperLimit.val[1] > 0 && upperLimit.val[2] > 0){
            hSlider.setSelectedMaxValue(upperLimit.val[0]);
            sSlider.setSelectedMaxValue(upperLimit.val[1]);
            vSlider.setSelectedMaxValue(upperLimit.val[2]);
            //}
        }
    }

    public void load(View view){
        loadData();
    }

    public static void runClient(Socket clientSocket){
        if(clientRun){
            Client myClient = new Client();
            myClient.execute(clientSocket);
        }
        else{
            clientRun = true;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(predictiveEnabled){
            final float alpha = 0.8f;
            Sensor mySensor = event.sensor;
            if(mySensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];

                gravity[0] = alpha * gravity[0] + (1-alpha) * event.values[0];
                gravity[1] = alpha * gravity[1] + (1-alpha) * event.values[1];
                gravity[2] = alpha * gravity[2] + (1-alpha) * event.values[2];

                acceleration[0] = event.values[0] - gravity[0];
                acceleration[1] = event.values[1] - gravity[1];
                acceleration[2] = event.values[2] - gravity[2];

                long curTime = System.currentTimeMillis();
                if ((curTime - lastUpdate) > 100) {
                    long diffTime = (curTime - lastUpdate);
                    lastUpdate = curTime;
                    last_x = acceleration[0];
                    offset = prediction.calcPredicted(last_x);
                    last_y = acceleration[1];
                    last_z = acceleration[2];
                }
                Log.d("Accelerometer:", "x: " + last_x + " y: " + last_y + " z: " + last_z);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
