package com.qutas.carcontroller;

import android.content.Context;
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
    CameraBridgeViewBase cameraHandler;
    Context callbackContext;

    private ColorBlobDetector    detectorPath;

    private final Scalar COLOR_RED =    new Scalar(255,  0,  0);
    private final Scalar COLOR_YELLOW = new Scalar(200,200,  0);
    private final Scalar COLOR_GREEN =  new Scalar(  0,255,  0);
    private final Scalar COLOR_CYAN  =  new Scalar(  0,200,200);
    private final Scalar COLOR_BLUE =   new Scalar(  0,  0,255);
    private final Scalar COLOR_PURPLE = new Scalar(200,  0,200);
    private final Scalar COLOR_BLACK =  new Scalar(  0,  0,  0);
    private final Scalar COLOR_WHITE =  new Scalar(255, 255,255);

    private final Scalar CLEARANCE_MIN =  new Scalar(255, 255,255);
    private final Scalar CLEARANCE_MAX =  new Scalar(255, 255,255);


    private Mat imgDisplay;
    private Mat imgProcess;
    private final double processHeight = 0.4;
    public double  targetSteer = 0;

    public double errIntegral = 0;
    public double oldErr = 0;

    public final double kI = 0.08;
    public final double kP = 0.5;
    public final double kD = 0.08;


    BaseLoaderCallback mLoaderCallback;

    public PathFinder(CameraBridgeViewBase cameraHandler, Context callbackContext){

        mLoaderCallback = new BaseLoaderCallback(callbackContext) {
            @Override
            public void onManagerConnected(int status) {
                if (LoaderCallbackInterface.SUCCESS == status){
                    Log.w(TAG, "OpenCV loaded successfully");
                    cameraHandler.enableView();
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


        detectorPath = new ColorBlobDetector();
        this.callbackContext = callbackContext;

        cameraHandler.setCameraIndex(0);
        this.cameraHandler = cameraHandler;

        //TODO: Camera AE and WB settings

        this.cameraHandler.setVisibility(SurfaceView.VISIBLE);
    }//End constructor

    public void onCameraViewStarted(int width, int height) {
        //Init the image matrix to the camera output's dimensions
        imgDisplay = new Mat(height, width, CvType.CV_8UC4);
    }

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {

        imgDisplay = inputFrame.rgba();
        double imgWidthPx = imgDisplay.cols();

        double leftInnerEdgePx = 0;
        double rightInnerEdgePx = imgWidthPx;
        Point topLeft = new Point();
        topLeft.x = -1;
        Point topRight = new Point();
        topRight.x = -1;

        //The camera image will include a horizon - ignore rows above this point
        int upperRow = (int) (imgDisplay.height() * processHeight);

        Imgproc.line(imgDisplay, new Point(0, upperRow), new Point(imgWidthPx, upperRow), COLOR_BLACK, 1);
        imgProcess = imgDisplay.submat(upperRow,imgDisplay.rows(),0,imgDisplay.cols());

        List<MatOfPoint> contourPath = detectorPath
                .Load(imgProcess)
                .IncludeRange(CLEARANCE_MIN, CLEARANCE_MAX)
                .GetContours();

        double trackMidPx = (leftInnerEdgePx + rightInnerEdgePx) / 2;
        double currentErr = PxToNormal(trackMidPx, imgDisplay.cols());
        double diffErr = currentErr - oldErr;
        oldErr = currentErr;
        errIntegral = errIntegral + currentErr / 50;

        //Quick and dirty PID
        //TODO Implement a time delta value
        targetSteer = (currentErr * kP ) + (errIntegral * kI) + (diffErr * kD);

        if (targetSteer > 1) targetSteer = 1;
        if (targetSteer < -1) targetSteer = -1;

        //Draw markers
        Point wheelAngleMarker = new Point();
        wheelAngleMarker.x = NormalToPx(targetSteer, imgDisplay.cols());
        wheelAngleMarker.y = 12;
        Imgproc.drawMarker(imgDisplay, wheelAngleMarker, COLOR_BLACK, 1, 24, 5);
        Imgproc.drawMarker(imgDisplay, wheelAngleMarker, COLOR_WHITE, 1, 20, 2);

        Imgproc.rectangle(imgDisplay,
                new Rect(0,0,220, 40),
                COLOR_WHITE,-1);
        Imgproc.putText(imgDisplay,
                String.format("%3.2f", targetSteer),
                new Point(  10, 30),
                0, 1, COLOR_BLACK,2);

        return imgDisplay;
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
        imgDisplay.release();
        imgProcess.release();
    }

    public double PxToNormal(double value, int inputRange){
        //Convert to 0 to 1
        double zerotoone = value / inputRange;
        //Convert to -1 to 1
        return (zerotoone * 2) - 1;

    }

    public double NormalToPx(double value, int outputRange){
        //Convert to
        double zerotoone = (value + 1)/2;
        //COnvert to range
        return (zerotoone * outputRange);
    }

}