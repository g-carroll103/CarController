package com.qutas.carcontroller;
import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.osgi.OpenCVInterface;
//import com.qutas.carcontroller.ColorBlobDetector;

import java.util.List;

public class PathFinder {


    final String TAG = "PathFinder";
    CameraBridgeViewBase camBVB;
    Context callbackContext;

    //private ColorBlobDetector    detectorB;
    private ColorBlobDetector    detectorY;

    private Scalar CONTOUR_COLOR;
    private Scalar mBlobColorRgba;
    private Scalar mBlobColorHsv;
    private  Mat mSpectrum;


    private Mat mRgba;

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

        this.callbackContext = callbackContext;
        this.camBVB = camBVB;
        this.camBVB.setCameraIndex(0);
        this.camBVB.setVisibility(SurfaceView.VISIBLE);
        //this.camBVB.setMaxFrameSize(320, 240);
        Log.w(TAG, "End of pf Constructor");
    }//End constructor

    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();
        detectorY.process(mRgba);
        List<MatOfPoint> contoursY = detectorY.getContours();
        Log.w(TAG, "Drawing Contours");

        Imgproc.drawContours(mRgba, contoursY, -1, CONTOUR_COLOR);
        Log.w(TAG, "onCameraFrame");

        // Draws a small block of solid color from (0,0) to (10,10): no longer needed
        //Mat colorLabel = mRgba.submat(0, 10, 0, 10);
        //colorLabel.setTo(mBlobColorRgba);
        return mRgba;
    }

    public void onCameraViewStarted(int width, int height) {
        Log.w(TAG, "onCameraViewStarted");
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        detectorY = new ColorBlobDetector();
        detectorY.setHsvColorRange(
                new Scalar(35, 80, 127, 0),   //Minimum values
                new Scalar(80, 255, 255, 255)
        );
        //detectorB = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba = new Scalar(255);
        mBlobColorHsv = new Scalar(255);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
    }

    public void onCameraViewStopped() {
        mRgba.release();
    }

}