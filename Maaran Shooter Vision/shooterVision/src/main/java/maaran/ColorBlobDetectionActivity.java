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
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;
import android.view.SurfaceView;

public class ColorBlobDetectionActivity extends Activity implements CvCameraViewListener2{
    private static final String  TAG = "OCV::Activity"; //Filter by this tag to see specific printouts

    private Mat rgbaColors; //frame
    private ColorBlobDetector colorDetector;
    private Scalar contourColor;

    private CameraBridgeViewBase cvCameraView;
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

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {

        Log.i(TAG, "called onCreate");
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.color_blob_detection_surface_view);

        cvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        cvCameraView.setMaxFrameSize(800,600); //change this to any valid camera resolution
        cvCameraView.setKeepScreenOn(true);
        cvCameraView.enableFpsMeter();
        cvCameraView.setVisibility(SurfaceView.VISIBLE);
        cvCameraView.setCvCameraViewListener(this);
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (cvCameraView != null)
            cvCameraView.disableView();
    }

    @Override
    public void onResume()
    {
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
        colorDetector = new ColorBlobDetector();
        contourColor = new Scalar(255,0,0,255);
    }

    public void onCameraViewStopped() {
        rgbaColors.release();
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        rgbaColors = inputFrame.rgba(); //takes rgba frame (a is just for opacity)
        colorDetector.process(rgbaColors);
        if(colorDetector.bestContour()) { //if there is a contour worth tracking
            Rect rect = colorDetector.getRect();
            Point topL = new Point(rect.x, rect.y);
            Point botR = new Point(rect.x + rect.width, rect.y + rect.height);
            //double xCentroid = rect.x + (rect.width/2);
            //Log.e("xCentroid"," " + xCentroid);
            Imgproc.rectangle(rgbaColors, topL, botR, contourColor, 10); //draws rectangle over the current frame before returning it
        }
        return rgbaColors;
    }

}
