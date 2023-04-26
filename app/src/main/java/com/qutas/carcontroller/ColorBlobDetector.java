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

import org.opencv.core.Core;

public class ColorBlobDetector {

    // Lower and Upper bounds for range checking in HSV color space
    // Minimum contour area in percent for contours filtering
    private static double mMinContourArea = 0.005;
    // Color radius for range checking in HSV color space
    private List<MatOfPoint> mContours = new ArrayList<MatOfPoint>();

    final int IMAGE_HALF_COUNT = 1;

    // Cache
    Mat mPyrDownMat = new Mat();
    Mat mHsv = new Mat();
    Mat mMask = new Mat();
    Mat mDilatedMask = new Mat();
    Mat mHierarchy = new Mat();

    public ColorBlobDetector(){
    }

    public ColorBlobDetector Load(Mat rgbaImage){
        //downsample image for processing by 2x
        Imgproc.pyrDown(rgbaImage, mPyrDownMat);

        //Convert from RGB to HSV
        Imgproc.cvtColor(mPyrDownMat, mHsv, Imgproc.COLOR_RGB2HSV_FULL);
        this.mMask = new Mat(this.mHsv.rows(), this.mHsv.cols(), CvType.CV_8U, Scalar.all(0));
        return this;
    }

    public ColorBlobDetector IncludeRange(Scalar mLowerBound, Scalar mUpperBound){
        Mat mTemp = new Mat();
        Core.inRange(mHsv, mLowerBound, mUpperBound, mTemp);
        Core.bitwise_or(mMask, mTemp, mMask);
        return this;
    }

    public ColorBlobDetector ExcludeRange(Scalar mLowerBound, Scalar mUpperBound){
        Mat mTemp = new Mat();
        Core.inRange(mHsv, mLowerBound, mUpperBound, mTemp);
        Core.bitwise_not(mMask,mMask,mTemp);

        return this;
    }

    public List<MatOfPoint> GetContours() {
        Imgproc.dilate(mMask, mDilatedMask, new Mat());
        // Find blobs from the image mask, store in contours list
        List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
        Imgproc.findContours(mDilatedMask, contours, mHierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

        mContours.clear();
        Iterator<MatOfPoint> each = contours.iterator();


        if (mMinContourArea > 0){
            // Filter contours by area and resize to fit the original image size
            // Find max contour area
            double minPixels =  mMinContourArea*mPyrDownMat.rows() * mPyrDownMat.cols();
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
            while (each.hasNext()) {
                MatOfPoint contour = each.next();
                Core.multiply(contour, new Scalar(2,2), contour);
                mContours.add(contour);
            }
        }
        return mContours;
    }
}
