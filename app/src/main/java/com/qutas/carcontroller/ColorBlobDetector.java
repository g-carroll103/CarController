package com.qutas.carcontroller;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ColorBlobDetector {


    // Minimum contour area in percent for contours filtering
    static double minContourArea = 0;
    static int kernelSize = 5;


    private List<MatOfPoint> mContours = new ArrayList<>();

    // Cached variables
    Mat rgbImage = new Mat();
    Mat mHsv = new Mat();
    Mat mMask = new Mat();
    Mat mDilatedMask = new Mat();

    public ColorBlobDetector() {
        //No operations needed to initialise this class
    }

    public ColorBlobDetector Load(Mat rgbImage) {
        //Convert from RGB to HSV
        Imgproc.cvtColor(rgbImage, mHsv, Imgproc.COLOR_RGB2HSV_FULL);
        //Create a binary mask matching the size of the matrix
        this.mMask = new Mat(this.mHsv.rows(), this.mHsv.cols(), CvType.CV_8U, Scalar.all(0));
        return this;
    }

    public ColorBlobDetector IncludeRange(Scalar mLowerBound, Scalar mUpperBound) {
        // Create temporary matrix to store the range mask
        Mat mTemp = new Mat();
        // Apply mask to temporary range
        Core.inRange(mHsv, mLowerBound, mUpperBound, mTemp);
        // Combine new range and running binary mask
        Core.bitwise_or(mMask, mTemp, mMask);
        return this;
    }

    public ColorBlobDetector ExcludeRange(Scalar mLowerBound, Scalar mUpperBound) {
        Mat mInRange = new Mat();
        //Check which values are within the upper and lower bounds
        Core.inRange(mHsv, mLowerBound, mUpperBound, mInRange);
        //Invert the mask to get pixels outside the range
        Core.bitwise_not(mInRange, mInRange);
        //Filter out any pixels in the inverted mask
        Core.bitwise_and(mMask, mInRange, mMask);
        mInRange.release();
        return this;
    }

    public List<MatOfPoint> GetContours() {
        //Dilate mask to denoise and simplify geometry
        Imgproc.dilate(mMask, mDilatedMask, GetErosionKernel());
        // Find blobs from the binary mask, store in contours list
        List<MatOfPoint> newcontours = new ArrayList<>();
        Imgproc.findContours(mDilatedMask, newcontours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        //Clear old list of contours
        mContours.clear();
        //Calculate the minimum number of pixels needed to qualify
        double minPixels = minContourArea * rgbImage.rows() * rgbImage.cols();
        // Filter contours by area
        Iterator<MatOfPoint> each = newcontours.iterator();
        while (each.hasNext()) {
            MatOfPoint contour = each.next();
            if (Imgproc.contourArea(contour) > minPixels) {
                mContours.add(contour);
            }
        } // Next
        return mContours;
    }

    public static Point GetCentroid(MatOfPoint contour) {
        Moments moments = Imgproc.moments(contour);
        return new Point(
                moments.get_m10() / moments.get_m00(),
                moments.get_m01() / moments.get_m00()
        );
    }

    public Mat GetErosionKernel() {
        return  Imgproc.getStructuringElement(Imgproc.CV_SHAPE_ELLIPSE, new Size(2 * kernelSize + 1, 2 * kernelSize + 1));
    }
}
