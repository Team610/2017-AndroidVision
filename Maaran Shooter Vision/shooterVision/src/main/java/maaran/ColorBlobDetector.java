package maaran;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class ColorBlobDetector {
    // Lower and Upper bounds for range checking in HSV color space
    private Scalar upperLimit = new Scalar(0);
    private Scalar lowerLimit = new Scalar(0);

    private MatOfPoint bestContour = new MatOfPoint(); //stores best contour

    private Rect rect = new Rect(0,0,0,0); //the bounding rect made when you have a best contour

    private boolean isBestContour = false;

    Mat mHsvMat = new Mat();
    Mat mMask = new Mat();
    Mat mHierarchy = new Mat();
    List<MatOfPoint> contours = new ArrayList<MatOfPoint>(); //list for storing all contours
    double maximumArea;

    public void setupHSV(){
        lowerLimit.val[0] = 0;
        lowerLimit.val[1] = 0;
        lowerLimit.val[2] = 0;

        upperLimit.val[0] = 0;
        upperLimit.val[1] = 0;
        upperLimit.val[2] = 0;
    }

    public void process(Mat rgbaImage) {

        Imgproc.cvtColor(rgbaImage, mHsvMat, Imgproc.COLOR_RGB2HSV); //converts image to hsv

        Core.inRange(mHsvMat, lowerLimit, upperLimit, mMask); //masks image

        contours.clear(); //clears list

        Imgproc.findContours(mMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE); //finds all contours
        maximumArea = 0; //used to track biggest contour
        //iterates through all contours and finds the biggest contour
        for (int i = 0; i < contours.size(); i++) {
            double a = Imgproc.contourArea(contours.get(i));
            if (a > maximumArea) {
                maximumArea = a;
                bestContour = contours.get(i);
                rect = Imgproc.boundingRect(bestContour); //rectangle around bestContour
            }
        }
        if(rect.area() > 1) //checks if it's worth tracking this contour
            isBestContour = true;
        else
            isBestContour = false;
    }

    public Mat maskedFrame(Mat rgbaImage){
        Imgproc.cvtColor(rgbaImage, mHsvMat, Imgproc.COLOR_RGB2HSV);
        Mat result = new Mat();
        Core.inRange(mHsvMat, lowerLimit,upperLimit, mMask);
        Core.bitwise_and(mMask,mMask,result);
        return result;
    }
    //Will be used to implement sliders later on
    public void setHSV(Scalar lowerT, Scalar upperT){
        for(int i = 0; i<3; i++){
            upperLimit.val[i] = upperT.val[i];
            lowerLimit.val[i] = lowerT.val[i];
        }

        Log.d("Loaded","HSV Updated");
    }

    public boolean isCreated(){return true;}
    public MatOfPoint getContours() {
        return bestContour;
    }

    public Rect getRect() {
        return rect;
    }
    public boolean bestContour() {
        return isBestContour;
    }
}
