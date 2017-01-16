package maaran;

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


    public void process(Mat rgbaImage) {
        //these will be removed once sliders are added
        upperLimit.val[0] = 100;
        upperLimit.val[1] = 100;
        upperLimit.val[2] = 100;
        upperLimit.val[3] = 0;

        lowerLimit.val[0] = 130;
        lowerLimit.val[1] = 255;
        lowerLimit.val[2] = 255;
        lowerLimit.val[3] = 255;


        Imgproc.cvtColor(rgbaImage, mHsvMat, Imgproc.COLOR_RGB2HSV); //converts image to hsv

        Core.inRange(mHsvMat, upperLimit, lowerLimit, mMask); //masks image

        List<MatOfPoint> contours = new ArrayList<MatOfPoint>(); //list for storing all contours

        Imgproc.findContours(mMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE); //finds all contours
        double maximumArea = 0; //used to track biggest contour
        //iterates through all contours and finds the biggest contour
        for (int i = 0; i < contours.size(); i++) {
            double a = Imgproc.contourArea(contours.get(i));
            if (a > maximumArea) {
                maximumArea = a;
                bestContour = contours.get(i);
                rect = Imgproc.boundingRect(bestContour); //rectangle around bestContour
            }
        }
        if(maximumArea > 1000) //checks if it's worth tracking this contour
            isBestContour = true;
        else
            isBestContour = false;
    }
    //Will be used to implement sliders later on
    public void setHSV(Scalar lowerT, Scalar upperT){
        for(int i = 0; i<3; i++){
            upperLimit.val[i] = upperT.val[i];
            lowerLimit.val[i] = upperT.val[i];
        }
    }
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
