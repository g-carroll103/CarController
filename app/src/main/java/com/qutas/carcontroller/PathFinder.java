package com.qutas.carcontroller;
import static com.qutas.carcontroller.Colors.*;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.View;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.List;

@SuppressLint("DefaultLocale")
public class PathFinder {

    final String TAG = "PathFinder";
    CameraBridgeViewBase cameraView;
    Context callbackContext;

    public ColorBlobDetector detectorPath;
    //public ColorBlobDetector detectorBlack = new ColorBlobDetector();
    //public ColorBlobDetector detectorFinishLine = new ColorBlobDetector();;

    //Any hue, brightness between 60 and 255, saturation between 0 and 120
    private final Scalar COLOR_MIN = new Scalar(0, 90, 80);
    private final Scalar COLOR_MAX = new Scalar(255, 255, 255);
    private final Scalar BLACK_MIN = new Scalar(0, 0, 0);
    private final Scalar BLACK_MAX = new Scalar(255, 255, 76);

    private final Scalar FINISH_LINE_MIN = new Scalar(50, 100, 255);
    private final Scalar FINISH_LINE_MAX = new Scalar(70, 255, 255);

    private Mat imgDisplay;
    private Mat imgProcess;
    private final double processHeight = 0;


    //TODO: Tune PID Controls
    public double targetSteer = 0;
    public double errIntegral = 0;
    public double oldErr = 0;
    public final double kI = 0.0;
    public final double kP = 0.5;
    public final double kD = 0.0;

    final int lineCount = 5;
    final int[] lineX = new int[lineCount];
    int[] lineHeight = new int[lineCount];


    BaseLoaderCallback mLoaderCallback;

    public PathFinder(CameraBridgeViewBase cameraView, Context callbackContext) {
        this.cameraView = cameraView;
        //Load the OpenCV library
        mLoaderCallback = new BaseLoaderCallback(callbackContext) {
            @Override
            public void onManagerConnected(int status) {
                if (LoaderCallbackInterface.SUCCESS == status) {
                    Log.w(TAG, "OpenCV loaded successfully in PF");
                    cameraView.enableView();
                } else {
                    Log.w(TAG, "LoaderCallbackInterface failed");
                }
            }
        };

        if (OpenCVLoader.initDebug()) {
            Log.w(TAG, "OpenCV w/ CUDA loaded");

        } else {
            Log.w(TAG, "OpenCV CUDA initialisation failed. Attempting fallback");

            OpenCVLoader.initDebug();
            Log.w(TAG, "OpenCV loaded from internal library");
            mLoaderCallback.onManagerConnected(LoaderCallbackInterface.SUCCESS);
        }

        cameraView.setCameraIndex(0);

        //TODO: Camera AE and WB settings


        detectorPath = new ColorBlobDetector();
        this.callbackContext = callbackContext;
        this.cameraView.setVisibility(View.VISIBLE);

    }//End constructor

    @SuppressLint("DefaultLocale")
    public Mat FindPath(Mat image) {
        imgDisplay = image;
        double imgWidthPx = image.cols();

        double leftInnerEdgePx = 0;
        double rightInnerEdgePx = imgWidthPx;
        Point topLeft = new Point();
        topLeft.x = -1;
        Point topRight = new Point();
        topRight.x = -1;

        //The camera image will include a horizon - ignore rows above this point
        int upperRow = (int) (imgDisplay.height() * processHeight);
        Imgproc.line(imgDisplay, new Point(0, upperRow), new Point(imgWidthPx, upperRow), Colors.COLOR_PURPLE, 3);

        //Crop processing image to below the horizon line
        imgProcess = imgDisplay.submat(upperRow, imgDisplay.rows(), 0, imgDisplay.cols());
        //Blur image to denoise
        Imgproc.blur(imgProcess, imgProcess, new Size(3,3));
        //Get contour of open road, not including any coloured regions
        List<MatOfPoint> obstacleList = detectorPath
                .Load(imgProcess)
                .IncludeRange(COLOR_MIN, COLOR_MAX)
                .ExcludeRange(FINISH_LINE_MIN, FINISH_LINE_MAX)
                .GetContours();

        //List <MatOfPoint> blackRegions = detectorBlack.Load(imgProcess).IncludeRange(BLACK_MAX, BLACK_MIN).GetContours();

        /* Causes an empty array error:
        // Defaults to middle of screen
        //TODO: Aim towards middle of largest contour

        //The columns are spread across the image like this:
        // -|--|--|--|--|-

        int lineSeparation = imgDisplay.cols() / (lineCount * 2);
        Log.w("PathFinder Column Spacing:", String.valueOf(lineSeparation));


        //Find lines on screen:
        for (int i = 0; i < lineCount; i++){
            //Calculate column position in image:
            lineX[i] = lineSeparation * (2*i+1);
            Log.w("PathFinder column position: ", String.valueOf(lineX[i]));
            //Set default fallback value:
            lineHeight[i] = -1;

            Mat hsvColumn = imgDisplay.col(lineX[i]);
            Log.wtf("PathFinder", String.format("Cols: %d, Rows: %d", hsvColumn.cols(), hsvColumn.rows()));
            for (int r = imgDisplay.rows(); r > 0; i--){
                double[] currentPx = hsvColumn.get(r, 0);
                //Check saturation and brightness values
                //if (currentPx[1] > 50 || currentPx[2] < 120 ) {
                //    lineHeight[i] = r;
                      Imgproc.line(imgDisplay, new Point(lineX[i], lineHeight[i]), new Point(lineX[i], imgDisplay.rows()), COLOR_PURPLE);

                //}

            }
        }


        //Draw lines:
        for (int i = 0; i < lineCount; i++){
        }

         */
        double trackMidPx = (leftInnerEdgePx + rightInnerEdgePx) / 2;

        double currentErr = PxToNormal(trackMidPx, imgDisplay.cols());

        targetSteer = SteeringControl(currentErr);

        //Draw Steering target marker
        //Point wheelAngleMarker = new Point( NormalToPx(targetSteer, imgDisplay.cols()), 12);
        //Point origin = new Point(trackMidPx, imgDisplay.rows());
        //Imgproc.line(imgDisplay, origin, wheelAngleMarker, COLOR_PURPLE, 2);

        // Display contours for debugging
        int contourNum = 0;
        for (MatOfPoint contour : obstacleList){
            Imgproc.drawContours(imgDisplay, obstacleList, contourNum, COLOR_GREEN, 2);
            Imgproc.putText(imgDisplay, String.valueOf(contourNum), ColorBlobDetector.GetCentroid(contour), Imgproc.FONT_HERSHEY_COMPLEX_SMALL, 1, COLOR_GREEN);
            contourNum++;
        }

        //Draw output text
        Imgproc.rectangle(imgDisplay,
                new Rect(0, 0, 220, 40), COLOR_WHITE, -1);
        Imgproc.putText(imgDisplay,
                String.format("%3d", obstacleList.size()),
                new Point(10, 30), 0, 1, COLOR_BLACK, 2);
        return imgDisplay;
    }

    public double SteeringControl(double error) {

        //Placeholder estimate of time between image frames
        //TODO: Implement an actual delta-time function
        final double delta_time = 1.0/50;

        double diffErr = (error - oldErr) / delta_time;
        oldErr = error;
        errIntegral = errIntegral + error * delta_time;

        //Quick and dirty PID
        targetSteer = (error * kP) + (errIntegral * kI) + (diffErr * kD);
        //Cap output to +/- 1
        if (targetSteer > 1) targetSteer = 1;
        if (targetSteer < -1) targetSteer = -1;
        return targetSteer;
    }

    public Mat GetColourValue(Mat rgbImage) {

        //Find the centre position of the image:
        int sampleX = rgbImage.cols() / 2;
        int sampleY = rgbImage.rows() / 2;
        //Get 1x1 pixel matrix from middle of the image, using row/column addressing:

        //Log.w("PathFinder", "GetColourValue source image size: " + String.format("%d x %d",rgbImage.rows(),rgbImage.cols()));
        Mat rgb1x1 = rgbImage.submat(sampleY, sampleY + 1, sampleX, sampleX + 1);
        //Log.w("PathFinder", "GetColourValue 1x1 size: " + String.format("%d x %d",rgb1x1.rows(),rgb1x1.cols()));
        Mat hsv1x1 = rgb1x1.clone();
        Imgproc.cvtColor(rgb1x1, hsv1x1, Imgproc.COLOR_RGB2HSV_FULL);

        //Get the RGB values of the centre pixel
        double[] rgbValue = rgb1x1.get(0, 0);
        double[] hsvValue = hsv1x1.get(0, 0);

        String rgbString = String.format("R: %3.0f  G: %3.0f  B: %3.0f", rgbValue[0], rgbValue[1], rgbValue[2]);
        String hsvString = String.format("H: %3.0f  S: %3.0f  V: %3.0f", hsvValue[0], hsvValue[1], hsvValue[2]);

        //Draw Marker around the sampled point:
        Imgproc.drawMarker(rgbImage, new Point(sampleX, sampleY), COLOR_BLACK, Imgproc.MARKER_DIAMOND, 30, 5);

        //Draw colour text on image
        //Box from -200, +50 to +100, +90
        Imgproc.rectangle(rgbImage, new Point(0, 0), new Point(600, 90), COLOR_WHITE, Imgproc.FILLED);
        //Text at -200, +50
        Imgproc.putText(rgbImage, rgbString, new Point(10, 40), Imgproc.FONT_HERSHEY_PLAIN, 3, COLOR_BLACK, 2);
        //Text at -200, +70
        Imgproc.putText(rgbImage, hsvString, new Point(10, 80), Imgproc.FONT_HERSHEY_PLAIN, 3, COLOR_BLACK, 2);

        Scalar rgbSampleColor = new Scalar(rgbValue);
        //Colour sample from -200, +100 to +100, +500
        Imgproc.rectangle(rgbImage, new Point(sampleX - 200, sampleY + 100), new Point(sampleX + 200, sampleY + 500), COLOR_BLACK, Imgproc.FILLED);
        Imgproc.rectangle(rgbImage, new Point(sampleX - 195, sampleY + 105), new Point(sampleX + 195, sampleY + 495), rgbSampleColor, Imgproc.FILLED);
        return rgbImage;
    }

    public Rect ContourToRect(List<MatOfPoint> contourList) {

        //Declaring empty lists:
        MatOfPoint2f[] contoursPoly = new MatOfPoint2f[contourList.size()];
        Rect[] boundRect = new Rect[contourList.size()];

        //Calculate contours of blobs:
        for (int i = 0; i < contourList.size(); i++) {
            contoursPoly[i] = new MatOfPoint2f();
            Imgproc.approxPolyDP(new MatOfPoint2f(contourList.get(i).toArray()), contoursPoly[i], 3, true);
            boundRect[i] = Imgproc.boundingRect(new MatOfPoint(contoursPoly[i].toArray()));
        }

        //Compare areas of contours to get the largest area
        int biggestBox = 0;
        for (int n = 1; n < boundRect.length - 1; n++) {
            if (boundRect[n].area() > boundRect[biggestBox].area()) {
                biggestBox = n;
            }
        }
        return boundRect[biggestBox];
    }

    public void onCameraViewStopped() {
        imgDisplay = new Mat();
        imgDisplay.release();

        imgProcess = new Mat();
        imgProcess.release();
    }

    public double PxToNormal(double value, int inputRange) {
        //Convert to decimal
        double zerotoone = value / inputRange;
        //Convert to -1 to 1
        return (zerotoone * 2) - 1;
    }

    public double NormalToPx(double value, int outputRange) {
        //Convert to
        double zerotoone = (value + 1) / 2;
        //COnvert to range
        return (zerotoone * outputRange);
    }
}