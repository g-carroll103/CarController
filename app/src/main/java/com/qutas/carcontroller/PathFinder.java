package com.qutas.carcontroller;

import static com.qutas.carcontroller.Colors.COLOR_BLACK;
import static com.qutas.carcontroller.Colors.COLOR_CYAN;
import static com.qutas.carcontroller.Colors.COLOR_GREEN;
import static com.qutas.carcontroller.Colors.COLOR_PURPLE;
import static com.qutas.carcontroller.Colors.COLOR_RED;
import static com.qutas.carcontroller.Colors.COLOR_WHITE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.mtp.MtpConstants;
import android.util.Log;
import android.view.View;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@SuppressLint("DefaultLocale")
public class PathFinder {

    final String TAG = "PathFinder";
    CameraBridgeViewBase cameraView;
    Context callbackContext;

    public ColorContourContainer detectorTrack;
    public ColorContourContainer detectorTrackHoles;
    public ColorContourContainer detectorArrow;
    public ColorContourContainer detectorFinishLine;
    public boolean safetyStop = true;
    public boolean finishStop = false;


    // HSV min/max limits
    private final Scalar TRACK_MIN = new Scalar(0, 0, 0);
    private final Scalar TRACK_MAX = new Scalar(255, 60, 255);

    private final Scalar ARROW_MIN = new Scalar(0, 0, 0);
    private final Scalar ARROW_MAX = new Scalar(255, 50, 70);

    private final Scalar FINISH_LINE_MIN = new Scalar(45, 110, 150);
    private final Scalar FINISH_LINE_MAX = new Scalar(65, 200, 255);

    //private final Scalar FINISH_LINE_MIN = new Scalar(200, 150, 100);
    //private final Scalar FINISH_LINE_MAX = new Scalar(255, 255, 255);

    private Mat mImgDisplay;
    private Mat imgProcess;
    public int finishFrame = -1;

    private int framesWithoutInput = 0;
    public double steeringOutput = 0;
    public double throttleOutput = 0;

    private PidController pid = new PidController(1, 0, 0);

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


        detectorTrack = new ColorContourContainer();
        detectorTrackHoles = new ColorContourContainer();
        detectorArrow = new ColorContourContainer();
        detectorFinishLine = new ColorContourContainer();

        this.callbackContext = callbackContext;
        this.cameraView.setVisibility(View.VISIBLE);

    }//End constructor

    public int invalid = 10000;
    public int FindLine(Mat imgProcess, Mat mImgDisplay, ColorContourContainer detector, double centre, double y)
    {
        int yPx = (int)y;

        int w = imgProcess.cols();
        //int h = imgProcess.rows();

        Mat line = detector.mFilteredMask.submat(yPx, yPx+5, 0, w);

        List<MatOfPoint> newContours = new ArrayList<>();
        Imgproc.findContours(line, newContours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        double currentDist = invalid;
        double currentPos = centre;
        for(MatOfPoint contour : newContours)
        {
            Point centroid = ColorContourContainer.GetCentroid(contour);
            double diff = Math.abs(centroid.x - centre);
            Imgproc.drawMarker(mImgDisplay,new Point(centroid.x, y), COLOR_GREEN, Imgproc.MARKER_TRIANGLE_DOWN, 30, 3);
            Imgproc.putText(mImgDisplay, String.format("%.2f", diff),
                    new Point(centroid.x, y), Imgproc.FONT_HERSHEY_COMPLEX_SMALL, 2, COLOR_BLACK);
            if(diff < currentDist)
            {
                currentDist = diff;
                currentPos = centroid.x;
            }
        }

        if(currentDist == invalid)
            return invalid;
        Imgproc.line(mImgDisplay, new Point(0, y), new Point(w, y), COLOR_CYAN, 5);


        Imgproc.drawMarker(mImgDisplay,new Point(currentPos, y), COLOR_WHITE, Imgproc.MARKER_TRIANGLE_DOWN, 30, 3);
        return (int) currentPos;
    }
    @SuppressLint("DefaultLocale")
    public Mat FindPath(Mat image) {
        mImgDisplay = image;
        Mat mAlphaBuffer = mImgDisplay.clone();

        final int imgWidthPx = image.cols();
        final int imgHeightPx = image.rows();

        final double trackLocateStartX = imgWidthPx*0.5;
        final double trackLocateStartY = imgHeightPx*0.95;
        final double finishY = imgHeightPx*0.8;
        final int centreLocateIncrement = -40; // this many pixels in Y direction per step
        final int centreLocateCount  = 15;
//        final int centreLocateIncrement = -80; // this many pixels in Y direction per step
//        final int centreLocateCount  = 2;
        final double centreLocateMul = 1;
        final double centreLocateWeightStart = 2;
        final double centreLocateWeightEnd = 2;
        final double centreLocateStartY = imgHeightPx*0.85;
        final double overrideY = imgHeightPx*0.855;
        final double overrideThresh = imgWidthPx*0.4/2;
        final double cutoffThresh = 200;
        final double overrideMul = 2;
        final double slowSpeed = 1;
        final double slowSpeedThresh = 0.5;

        imgProcess = mImgDisplay.clone();
        //Blur image to denoise
        Imgproc.blur(imgProcess, imgProcess, new Size(7,7));


        //Get contour of open road, not including any coloured regions
        List<MatOfPoint> trackContours = detectorTrack
                .Load(imgProcess)
                .IncludeRange(TRACK_MIN, TRACK_MAX)
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
        //double trackMidPx = (leftInnerEdgePx + rightInnerEdgePx) / 2;

        //double currentErr = PxToNormal(trackMidPx, imgDisplay.cols());

        // = SteeringControl(currentErr);

        //Draw Steering target marker
        //Point wheelAngleMarker = new Point( NormalToPx(targetSteer, imgDisplay.cols()), 12);
        //Point origin = new Point(trackMidPx, imgDisplay.rows());
        //Imgproc.line(imgDisplay, origin, wheelAngleMarker, COLOR_PURPLE, 2);

        // Display contours for debugging

        double steeringRequest = 0;
        Point trackLocateStart = new Point(trackLocateStartX, trackLocateStartY);
        int contourNum = 0;
        boolean trackContourFound = false;
        for (MatOfPoint contour : trackContours){
            if(Imgproc.pointPolygonTest(new MatOfPoint2f(contour.toArray()), trackLocateStart, false) > 0)
            {
                trackContourFound = true;
                break;
            }
            contourNum++;
        }
        if(trackContourFound) {
            detectorTrack.SelectContour(contourNum, false);
            detectorTrack.OverlayMask(mAlphaBuffer, COLOR_RED);
            //detectorTrack.SelectContour(contourNum, true);
            final MatOfPoint trackContour = trackContours.get(contourNum);
            final MatOfPoint2f trackContour2f = new MatOfPoint2f(trackContour.toArray());
            double xCentre = 0;
            double prevCentre = trackLocateStartX;
            for(int i = 0; i < centreLocateCount; i++)
            {
                double lerp = i/(centreLocateCount-1f);
                double y = centreLocateStartY+(i*centreLocateIncrement);
                double weight = (centreLocateWeightStart*(1-lerp))+(centreLocateWeightEnd*lerp);
                double xReal = FindLine(imgProcess,mImgDisplay,detectorTrack,
                        prevCentre, y);

                boolean isInvalid = false;
                if(xReal >= invalid)/// || (Math.abs(xReal-prevCentre) > cutoffThresh && i>0 )) {
                {
                    isInvalid = true;
                    if(prevCentre < trackLocateStartX)
                        xReal = 0;
                    else
                        xReal = imgWidthPx;
                    Imgproc.drawMarker(mImgDisplay, new Point(xReal, y), COLOR_RED, Imgproc.MARKER_TILTED_CROSS, 40, 5);

                    //prevCentre = xReal;
                    xCentre += (centreLocateCount - i - 1) * (xReal - trackLocateStartX) * centreLocateWeightEnd;
                    break;
                }//*/

                double xCentred = xReal - (trackLocateStartX);
                /*if(isInvalid)
                {
                    xCentre += (centreLocateCount - i - 1)*(xCentred-trackLocateStartX)*centreLocateWeightEnd;
                    break;
                }//*/


                xCentre += xCentred*weight;
                prevCentre = xReal;
            }
            //xCentre *= centreLocateMul;

            List<MatOfPoint> finishContours = detectorFinishLine
                    .Load(imgProcess)
                    .IncludeRange(FINISH_LINE_MIN, FINISH_LINE_MAX)
                    .GetContours();
            double curFinishY = 0;
            double finishArea = 0;
            for(MatOfPoint contour : finishContours)
            {
                double a = Imgproc.contourArea(contour);
                if(a > finishArea)
                {
                    finishArea = a;
                    curFinishY = ColorContourContainer.GetCentroid(contour).y;
                }
            }
            if(curFinishY > finishY && finishFrame < 0)
            {
                finishFrame = 0;
            }
            Imgproc.drawContours(mImgDisplay, finishContours, -1,
                    COLOR_GREEN, Imgproc.FILLED);


            xCentre /= centreLocateCount;

            double possibleOverride = FindLine(imgProcess,mImgDisplay,detectorTrack,
                    imgWidthPx/2.0, overrideY)-(trackLocateStartX);
            if(Math.abs(possibleOverride) > overrideThresh)
                xCentre = possibleOverride*overrideMul;

            double cxc = xCentre+trackLocateStartX;
            if(cxc > imgWidthPx)
                cxc = imgWidthPx;
            if(cxc < 0)
                cxc = 0;
            Imgproc.drawMarker(mImgDisplay, new Point(cxc, imgHeightPx/2.0),
                    COLOR_BLACK, Imgproc.MARKER_DIAMOND, 50, 10);

            steeringRequest = (xCentre / (imgWidthPx/2.0));
            if(steeringRequest > 1)
                steeringRequest = 1;
            if(steeringRequest < -1)
                steeringRequest = -1;

            List<MatOfPoint> arrowContours = detectorArrow
                    .Load(imgProcess)
                    .IncludeRange(ARROW_MIN, ARROW_MAX)
                    .Intersect(detectorTrack)
                    .GetContours();

            if(finishFrame >= 0)
            {
                finishFrame++;
                if(finishFrame > 10)
                {
                    finishStop = true;
                    finishFrame--;
                }
            }

            double tmpThr = 1;
            if(Math.abs(steeringRequest) > slowSpeedThresh)
                tmpThr = slowSpeed;
            throttleOutput = finishStop ? 0 : tmpThr;
            steeringOutput = pid.Run(steeringRequest, 1.0/10);

            //safetyStop = false;
            // transparent overlay
            final float alpha = 0.3f;
            Core.addWeighted(mImgDisplay, 1 - alpha, mAlphaBuffer, alpha, 0, mImgDisplay);
            framesWithoutInput = 0;

        }
        else
        {
            framesWithoutInput++;
            if(framesWithoutInput > 3)
            {
                throttleOutput = 0;
                steeringOutput = 0;
                safetyStop = true;
                framesWithoutInput--;
                finishFrame = -1;
                finishStop = false;
            }
        }


        Imgproc.drawMarker(mImgDisplay, trackLocateStart, new Scalar(255, 0, 255), Imgproc.MARKER_STAR, 30, 2);

        //Draw output text
        Imgproc.rectangle(mImgDisplay,
                new Rect(0, 0, 800, 40), COLOR_WHITE, -1);
        Imgproc.putText(mImgDisplay,
                String.format("S:%1.3f", steeringOutput),
                new Point(10, 30), 0, 1, COLOR_BLACK, 2);
        Imgproc.putText(mImgDisplay,
                String.format("T:%1.3f", throttleOutput),
                new Point(250, 30), 0, 1, COLOR_BLACK, 2);
        Imgproc.putText(mImgDisplay,
                String.format("F:%d", finishFrame),
                new Point(550, 30), 0, 1, COLOR_BLACK, 2);

        mAlphaBuffer.release();
        return mImgDisplay;
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
        mImgDisplay = new Mat();
        mImgDisplay.release();

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