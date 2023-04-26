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
    CameraBridgeViewBase camBVB;
    Context callbackContext;

    private ColorBlobDetector    detectorLeft;
    private ColorBlobDetector    detectorRight;
    private ColorBlobDetector detectorObstacle;

    private final Scalar COLOR_RED =    new Scalar(255,  0,  0);
    private final Scalar COLOR_YELLOW = new Scalar(200,200,  0);
    private final Scalar COLOR_GREEN =  new Scalar(  0,255,  0);
    private final Scalar COLOR_CYAN  =  new Scalar(  0,200,200);
    private final Scalar COLOR_BLUE =   new Scalar(  0,  0,255);
    private final Scalar COLOR_PURPLE = new Scalar(200,  0,200);
    private final Scalar COLOR_BLACK =  new Scalar(  0,  0,  0);
    private final Scalar COLOR_WHITE =  new Scalar(255, 255,255);

    private final Scalar ThreshTrackLeftMin = new Scalar(28, 55, 120);
    private final Scalar ThreshTrackLeftMax = new Scalar(42, 255, 255);

    private final Scalar ThreshTrackRightMin = new Scalar(28, 55, 120);
    private final Scalar ThreshTrackRightMax = new Scalar(42, 255, 255);

    private final Scalar ThreshObstacleMin = new Scalar(205, 71, 166 );
    private final Scalar ThreshObstacleMax = new Scalar(234, 255, 255);

    private Mat mShow;
    private Mat mProcess;
    private final double processHeight = 0.4;
    public double  targetSteer = 0;

    public double steerIntegral = 0;
    public double oldErr = 0;

    public final double kI = 0.08;
    public final double kP = 0.5;
    public final double kD = 0.08;


    BaseLoaderCallback mLoaderCallback;

    public PathFinder(CameraBridgeViewBase camBVB, Context callbackContext){

        mLoaderCallback = new BaseLoaderCallback(callbackContext) {
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
        detectorLeft = new ColorBlobDetector();
        //Blue
        detectorRight = new ColorBlobDetector();

        detectorObstacle = new ColorBlobDetector();

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

        mShow = inputFrame.rgba();
        double imgWidthPx = mShow.cols();

        double leftInnerEdgePx = 0;
        double rightInnerEdgePx = imgWidthPx;
        Point topLeft = new Point();
        topLeft.x = -1;
        Point topRight = new Point();
        topRight.x = -1;

        int upperRow = (int) (mShow.height() * processHeight);
        mProcess = mShow.submat(upperRow,mShow.rows(),0,mShow.cols());

        //Detect yellow regions
        List<MatOfPoint> contoursLeft = detectorLeft
                .Load(mProcess)
                .IncludeRange(ThreshTrackLeftMin, ThreshTrackLeftMax)
                .GetContours();

        //Detect blue regions
        List<MatOfPoint> contoursRight = detectorRight
                .Load(mProcess)
                .IncludeRange(ThreshTrackRightMin, ThreshTrackRightMax)
                .GetContours();


        if (contoursLeft.size() > 0) {
            Rect boxLeft = ContourToRect(contoursLeft);
            Imgproc.rectangle(mProcess,boxLeft,COLOR_YELLOW,2);
            leftInnerEdgePx = boxLeft.x + boxLeft.width;
            topLeft.x = boxLeft.x + boxLeft.width;
            topLeft.y = boxLeft.y + upperRow;
        }

        if (contoursRight.size() > 0) {
            Rect boxRight = ContourToRect(contoursRight);
            Imgproc.rectangle(mProcess,boxRight,COLOR_BLUE,2);
            rightInnerEdgePx  = boxRight.x;
            topRight.x = boxRight.x;
            topRight.y = boxRight.y + upperRow;
        }

        //If both regions are visible
        if (topLeft.x > 0 && topRight.x > 0){
            //Draw line between points
            Imgproc.line(mShow, topLeft, topRight, COLOR_CYAN, 2);
            //Mark middle of line
            Point midPoint = new Point();
            midPoint.x = (topLeft.x + topRight.x) / 2;
            midPoint.y = 20 +  (topLeft.y + topRight.y) / 2;
            Imgproc.drawMarker(mShow, midPoint, COLOR_CYAN, 5, 50, 4);
        }

        double trackMidPx = (leftInnerEdgePx + rightInnerEdgePx) / 2;

        double currentErr = PxToNormal(trackMidPx, mShow.cols());

        double diffErr = currentErr - oldErr;

        //Quick and dirty PID
        targetSteer = (currentErr * kP ) + (steerIntegral * kI) + (diffErr * kD);

        if (targetSteer > 1) targetSteer = 1;
        if (targetSteer < -1) targetSteer = -1;

        //Draw markers
        Point wheelAngleMarker = new Point();
        wheelAngleMarker.x = NormalToPx(targetSteer, mShow.cols());
        wheelAngleMarker.y = 10;
        Imgproc.drawMarker(mShow, wheelAngleMarker, COLOR_BLACK, 1, 24, 5);
        Imgproc.drawMarker(mShow, wheelAngleMarker, COLOR_WHITE, 1, 20, 2);


        ;

        Imgproc.rectangle(
                mShow,
                new Rect(0,mShow.rows()-40,200, 40),
                COLOR_WHITE,-1);
        Imgproc.putText(mShow,
                String.format("%3.2f", targetSteer),
                new Point(30, mShow.rows() - 10),
                0, 1, COLOR_BLACK,2);

        oldErr = currentErr;
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