package com.qutas.carcontroller;

import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

public class ColorBlobDetector {

    // Lower and Upper bounds for range checking in HSV color space
    private Scalar mLowerBound = new Scalar(0);
    private Scalar mUpperBound = new Scalar(0);
    // Minimum contour area in percent for contours filtering
    private static double mMinContourArea = 0.005;
    // Color radius for range checking in HSV color space
    private List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();

    final int IMAGE_HALF_COUNT = 1;

    // Cache
    Mat mPyrDownMat = new Mat();
    Mat mHsvMat = new Mat();
    Mat mMask = new Mat();
    Mat mDilatedMask = new Mat();
    Mat mHierarchy = new Mat();


    public ColorBlobDetector(Scalar hsvMin, Scalar hsvMax){
        mLowerBound = hsvMin;
        mUpperBound = hsvMax;
    }

    public void process(Mat rgbaImage) {

        //downsample image for processing by 2x
        Imgproc.pyrDown(rgbaImage, mPyrDownMat);

        //Convert from RGB to HSV
        Imgproc.cvtColor(mPyrDownMat, mHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

        //Find colour values from mHsvMat in the range of mLowerBound to mUpperBound, set in mMask
        Core.inRange(mHsvMat, mLowerBound, mUpperBound, mMask);
        Imgproc.dilate(mMask, mDilatedMask, new Mat());
        Imgproc.dilate(mDilatedMask, mDilatedMask, new Mat());

        // Find blobs from the image mask, store in contours list
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        if (mMinContourArea > 0){
        // Filter contours by area and resize to fit the original image size
            // Find max contour area
            double minPixels =  mMinContourArea*mPyrDownMat.rows() * mPyrDownMat.cols();
            Iterator<MatOfPoint> each = contours.iterator();
            mContours.clear();
            each = contours.iterator();
            while (each.hasNext()) {
                MatOfPoint contour = each.next();
                if (Imgproc.contourArea(contour) > minPixels) {
                    Core.multiply(contour, new Scalar(2,2), contour);
                    mContours.add(contour);
                }
            }
        }
        else {
            Iterator<MatOfPoint> each = contours.iterator();
            mContours.clear();
            while (each.hasNext()) {
                    MatOfPoint contour = each.next();
                    Core.multiply(contour, new Scalar(2,2), contour);
                    mContours.add(contour);
                }
            }
        }

    public List<MatOfPoint> getContours() {
        return mContours;
    }
}
