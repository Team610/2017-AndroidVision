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
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
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
import java.util.List;


public class ColorBlobDetectionActivity extends Activity implements CvCameraViewListener2, RangeSeekBar.OnRangeSeekBarChangeListener{
    private static final String  TAG = "OCV::Activity"; //Filter by this tag to see specific printouts

    private static final int port = 3800;
    public final String host = "localhost";
    private final String TAGOne = "Communication";

    private String message;

    private Socket socket;


    private Mat rgbaColors; //frame
    private Mat smallerFrame;
    private ColorBlobDetector colorDetector;
    private Scalar contourColor;
    private ViewGroup sliderView;
    private ViewGroup presetView;

    private RangeSeekBar hSlider;
    private RangeSeekBar sSlider;
    private RangeSeekBar vSlider;

    private  boolean cameraCreated;

    Rect rect;
    Point topL;
    Point botR;

    private Scalar upperLimit = new Scalar(0);
    private Scalar lowerLimit = new Scalar(0);

    private View option;

    private boolean sliderShow = false;

    private CameraBridgeViewBase cvCameraView;

    private double xCentroid = 0;

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
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);
        sliderView = (ViewGroup) findViewById(R.id.sliders);
        option = (View) findViewById(R.id.options);
        presetView = (ViewGroup) findViewById(R.id.presets);

        hSlider = (RangeSeekBar) findViewById(R.id.hSlider);
        hSlider.setOnRangeSeekBarChangeListener(this);
        sSlider = (RangeSeekBar) findViewById(R.id.sSlider);
        sSlider.setOnRangeSeekBarChangeListener(this);
        vSlider = (RangeSeekBar) findViewById(R.id.vSlider);
        vSlider.setOnRangeSeekBarChangeListener(this);

        cvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        cvCameraView.setMaxFrameSize(480,320); //change this to any valid camera resolution
        cvCameraView.setKeepScreenOn(true);
        cvCameraView.enableFpsMeter();
        cvCameraView.setVisibility(SurfaceView.VISIBLE);
        cvCameraView.setCvCameraViewListener(this);

        option.bringToFront();

        rect = new Rect();
        topL = new Point();
        botR = new Point();


    }

    @Override
    public void onPause() {
        super.onDestroy();
        super.onPause();
        if (cvCameraView != null)
            cvCameraView.disableView();
    }

    @Override
    public void onResume() {
        super.onResume();
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
        smallerFrame = new Mat(height/2, width/2, CvType.CV_8UC4);
        contourColor = new Scalar(255,0,0,255);
        colorDetector = new ColorBlobDetector();
        colorDetector.setupHSV();
        loadData();
        cameraCreated = true;
        loadData();
    }

    public void onCameraViewStopped() {
        rgbaColors.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        message = null;
        if(socket == null){
            try{
                Log.d(TAGOne,"Trying to connect");
                socket= new Socket(host,port);
                socket.setSoTimeout(100);
            }catch (IOException e){
                socket = null;
            }
        }
        rgbaColors = inputFrame.rgba(); //takes rgba frame (a is just for opacity)
        colorDetector.process(rgbaColors);
        if(!sliderShow) {
            if (colorDetector.bestContour()) { //if there is a contour worth tracking
                rect = colorDetector.getRect();
                topL = new Point(rect.x, rect.y);
                botR = new Point(rect.x + rect.width, rect.y + rect.height);
                xCentroid = rect.x + (rect.width/2);
                message = xCentroid + "/n";
                Log.d("xCentroid"," " + xCentroid);
                Imgproc.rectangle(rgbaColors, topL, botR, contourColor, 10); //draws rectangle over the current frame before returning it

            }
        }
        else{
            rgbaColors = colorDetector.maskedFrame(rgbaColors);
        }
        if(message!=null && socket != null && socket.isConnected()){
            try{
                Log.d(TAGOne,"Trying to write data");
                OutputStream os = socket.getOutputStream();
                os.write(message.getBytes());
            } catch (IOException e){
                socket = null;
            }
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

    @Override
    public void onRangeSeekBarValuesChanged(RangeSeekBar bar, Object minValue, Object maxValue) {
        //Log.d("Bar"," "+ bar.getId());
        if((int)bar.getId() == 2131427395) { //hbar id
            upperLimit.val[0] = bar.getSelectedMaxValue().doubleValue();
            lowerLimit.val[0] = bar.getSelectedMinValue().doubleValue();
        }
        if((int)bar.getId() == 2131427396) { //sbar id
            upperLimit.val[1] = bar.getSelectedMaxValue().doubleValue();
            lowerLimit.val[1] = bar.getSelectedMinValue().doubleValue();
        }
        if((int)bar.getId() == 2131427397) { //vbar id
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


}
