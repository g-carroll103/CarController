package com.qutas.carcontroller;
import static java.lang.Float.NaN;
import static java.lang.Float.isNaN;

import android.content.Context;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontStyle;
import android.util.Log;
import android.view.SurfaceView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import java.util.List;

public class PathFinder {

    final String TAG = "PathFinder";
    CameraBridgeViewBase camBVB;
    Context callbackContext;

    private ColorBlobDetector    detectorLeft;
    private ColorBlobDetector    detectorRight;

    private final Scalar COLOR_RED =    new Scalar(255,  0,  0);
    private final Scalar COLOR_YELLOW = new Scalar(200,200,  0);
    private final Scalar COLOR_GREEN =  new Scalar(  0,255,  0);
    private final Scalar COLOR_CYAN  =  new Scalar(  0,200,200);
    private final Scalar COLOR_BLUE =   new Scalar(  0,  0,255);
    private final Scalar COLOR_PURPLE = new Scalar(200,  0,200);
    private final Scalar COLOR_BLACK =  new Scalar(  0,  0,  0);

    private Mat mShow;
    private Mat mProcess;
    private final double processHeight = 0.4;
    public double  targetSteer = 0;
    public final double incrementWeight = 0.01;

    public Point testPointL;
    public Point testPointR;

    BaseLoaderCallback mLoaderCallback;

    public PathFinder(CameraBridgeViewBase camBVB, Context callbackContext){

        mLoaderCallback = new BaseLoaderCallback() {
            @Override
            public void onManagerConnected(int status) {
                if (LoaderCallbackInterface.SUCCESS == status){
                    Log.w(TAG, "OpenCV loaded successfully");
                    camBVB.enableView();

                }
                else {
                    Log.w(TAG, "LoaderCallbackInterface failed");
                }
            }
        };

        if (!OpenCVLoader.initDebug()) {
            Log.w(TAG, "Internal OpenCV library not found. Using OpenCV Manager for initialization");
            OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_3_0_0, callbackContext, mLoaderCallback);
        }
        else {
            Log.w(TAG, "OpenCV loaded from internal library");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        //Yellow
        detectorLeft = new ColorBlobDetector(
                new Scalar(28, 55, 120),
                new Scalar(42, 255, 255)
        );
        //Blue
        detectorRight = new ColorBlobDetector(
                new Scalar(122, 35, 120),
                new Scalar(170, 255, 255)
        );
        this.callbackContext = callbackContext;
        this.camBVB = camBVB;
        this.camBVB.setCameraIndex(0);
        //TODO: Camera AE and WB

        this.camBVB.setVisibility(SurfaceView.VISIBLE);
        Log.w(TAG, "End of pf Constructor");
    }//End constructor

    public void onCameraViewStarted(int width, int height) {
        mShow = new Mat(height, width, CvType.CV_8UC4);
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        double leftEdge = 0;
        double rightEdge = mShow.cols();
        Point topLeft = new Point();
        topLeft.x = -1;
        Point topRight = new Point();
        topRight.x = -1;

        double incrementSteer = 0;

        mShow = inputFrame.rgba();

        int upperRow = (int) (mShow.height() * processHeight);
                mProcess = mShow.submat(
                upperRow,
                mShow.rows(),
                        0,
                mShow.cols()
        );

        //Detect yellow regions
        detectorLeft.process(mProcess);
        List<MatOfPoint> contoursLeft = detectorLeft.getContours();

        if (contoursLeft.size() > 0) {
            Rect boxLeft = ContourToRect(contoursLeft);
            testPointL = new Point(
                    boxLeft.x + boxLeft.width/2,
                    boxLeft.y + boxLeft.height/2);
            Imgproc.drawMarker(mProcess, testPointL, COLOR_GREEN,  0, 20, 3);
            //Imgproc.drawContours(mRgba, contoursLeft, -1, COLOR_GREEN, 1);
            Imgproc.rectangle(mProcess,boxLeft,COLOR_GREEN,2);
            leftEdge = boxLeft.x + boxLeft.width;
            topLeft.x = boxLeft.x + boxLeft.width;
            topLeft.y = boxLeft.y + upperRow;
        }

        //Detect blue regions
        detectorRight.process(mProcess);
        List<MatOfPoint> contoursRight = detectorRight.getContours();

        if (contoursRight.size() > 0) {
            Rect boxRight = ContourToRect(contoursRight);
            testPointR = new Point(
                    boxRight.x + boxRight.width/2,
                    boxRight.y + boxRight.height/2);
            Imgproc.drawMarker(mProcess, testPointR, COLOR_PURPLE, 0, 20, 3);
            Imgproc.rectangle(mProcess,boxRight,COLOR_PURPLE,2);
            rightEdge  = boxRight.x;
            topRight.x = boxRight.x;
            topRight.y = boxRight.y + upperRow;
        }

        if (topLeft.x > 0 && topRight.x > 0){
            //Draw line between points
            Imgproc.line(mShow, topLeft, topRight, COLOR_CYAN, 2);
            //Draw middle of line
            Point midPoint = new Point();
            midPoint.x = (topLeft.x + topRight.x) / 2;
            midPoint.y = 20 +  (topLeft.y + topRight.y) / 2;
            Imgproc.drawMarker(mShow, midPoint, COLOR_CYAN, 5, 50, 4);
        }

        double leftMarkerPos = scaleDownInt(leftEdge, mShow.cols());
        double rightMarkerPos = scaleDownInt(leftEdge, mShow.cols());
        incrementSteer =  - scaleDownInt(rightEdge, mShow.cols())) / 2;

        targetSteer = targetSteer + incrementSteer * incrementWeight;
        if (targetSteer > 1) targetSteer = 1;
        if (targetSteer < -1) targetSteer = -1;

        Point wheelAngle = new Point();
        wheelAngle.x = scaleUpDouble(targetSteer, mShow.cols());
        wheelAngle.y = 10;
        Imgproc.drawMarker(mShow, wheelAngle, COLOR_BLACK, 1, 20, 5);

        String steeringText = String.format("%3.2f",targetSteer );

        Log.w("Steering", steeringText);

        return mShow;
    }

    public Rect ContourToRect(List<MatOfPoint> contourList){
        MatOfPoint2f[] contoursPoly  = new MatOfPoint2f[contourList.size()];
        Rect[] boundRect = new Rect[contourList.size()];

        for (int i = 0; i < contourList.size(); i++) {
            contoursPoly[i] = new MatOfPoint2f();
            Imgproc.approxPolyDP(new MatOfPoint2f(contourList.get(i).toArray()), contoursPoly[i], 3, true);
            boundRect[i] = Imgproc.boundingRect(new MatOfPoint(contoursPoly[i].toArray()));
        }

        int biggestBox = 0;
        for (int n = 1; n < boundRect.length - 1; n++){
            if (boundRect[n].area() > boundRect[biggestBox].area()){
                biggestBox = n;
            }
        }
        return boundRect[biggestBox];
    }

    public void onCameraViewStopped() {
        mShow.release();
        mProcess.release();
    }

    public double scaleDownInt(double value, int inputRange){
        double downscaled = value / inputRange;
        double out =  (downscaled * 2) - 1;
        return out;
    }

    public int scaleUpInt(double value, int outputRange){
        double downscaled = (value + 1)/2;
        int outval = (int)(downscaled * outputRange);
        return outval;
    }

    public double scaleUpDouble(double value, int outputRange){
        double downscaled = (value + 1)/2;
        double outval = (downscaled * outputRange);
        return outval;
    }

}