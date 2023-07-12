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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class ColorContourContainer {


    // Minimum contour area in percent for contours filtering
    static double minContourArea = 0;
    int erodeKernelSize;
    int dilateKernelSize;


    private List<MatOfPoint> mContours = new ArrayList<>();

    // Cached variables
    Mat mRawImage = new Mat();
    Mat mOperating = new Mat();
    Mat mRawMask = new Mat();
    Mat mFilteredMask = new Mat();

    int selectedContour = -1;

    public ColorContourContainer(){
        this(7, 0);
    }
    public ColorContourContainer(int erodeKernelSize, int dilateKernelSize) {
        this.erodeKernelSize = erodeKernelSize;
        this.dilateKernelSize = dilateKernelSize;
    }

    public ColorContourContainer Load(Mat rgbImage) {
        //Convert from RGB to HSV
        Imgproc.cvtColor(rgbImage, mOperating, Imgproc.COLOR_RGB2HSV_FULL);
        //Create a binary mask matching the size of the matrix
        this.mRawMask = new Mat(this.mOperating.rows(), this.mOperating.cols(), CvType.CV_8U, Scalar.all(0));
        return this;
    }

    public ColorContourContainer IncludeRange(Scalar mLowerBound, Scalar mUpperBound) {
        // Create temporary matrix to store the range mask
        Mat mTemp = new Mat();
        // Apply mask to temporary range
        Core.inRange(mOperating, mLowerBound, mUpperBound, mTemp);
        // Combine new range and running binary mask
        Core.bitwise_or(mRawMask, mTemp, mRawMask);
        return this;
    }

    public ColorContourContainer ExcludeRange(Scalar mLowerBound, Scalar mUpperBound) {
        Mat mInRange = new Mat();
        //Check which values are within the upper and lower bounds
        Core.inRange(mOperating, mLowerBound, mUpperBound, mInRange);
        //Invert the mask to get pixels outside the range
        Core.bitwise_not(mInRange, mInRange);
        //Filter out any pixels in the inverted mask
        Core.bitwise_and(mRawMask, mInRange, mRawMask);
        mInRange.release();
        return this;
    }

    public ColorContourContainer Intersect(ColorContourContainer intersection)
    {
        if(intersection.mRawMask.size().equals(mRawMask.size()))
        {
            Core.bitwise_and(mRawMask, intersection.mRawMask, mRawMask);
        }
        return this;
    }

    public ColorContourContainer Exclude(ColorContourContainer intersection)
    {
        if(intersection.mRawMask.size().equals(mRawMask.size()))
        {
            Mat tmp = intersection.mRawMask.clone();
            Core.bitwise_not(tmp, tmp);
            Core.bitwise_and(mRawMask, tmp, mRawMask);
            tmp.release();
        }
        return this;
    }

    public void OverlayMask(Mat img, Scalar color)
    {
        OverlayMask(img, mFilteredMask, color);
    }
    static public void OverlayMask(Mat img, Mat inMask, Scalar color)
    {
        Mat colorMat = new Mat(inMask.rows(), inMask.cols(), CvType.CV_8UC4, color);
        Core.bitwise_or(img, colorMat, img, inMask);
        Core.bitwise_and(img, colorMat, img, inMask);

        colorMat.release();
    }
    public ColorContourContainer SelectContour(int contour, boolean fillHoles)
    {
        if(contour >= 0 && contour < mContours.size()) {
            selectedContour = contour;
            mFilteredMask.setTo(Scalar.all(0));
            Imgproc.fillPoly(mFilteredMask, Collections.singletonList(mContours.get(selectedContour)),
                    Scalar.all(255));

            if(!fillHoles) {
                // remove "hole" contours
                List<MatOfPoint> tmp = new ArrayList<>();
                tmp.addAll(mContours);
                tmp.remove(contour);
                Imgproc.fillPoly(mFilteredMask, tmp, Scalar.all(0));
            }
        }
        return this;
    }

    public Mat GetRawMask()
    {
        return mRawMask;
    }

    public List<MatOfPoint> GetContours() {
        Imgproc.erode(mRawMask, mFilteredMask, GetErosionKernel());
        Imgproc.dilate(mRawMask, mFilteredMask, GetDilationKernel());
        // Find blobs from the binary mask, store in contours list
        List<MatOfPoint> newContours = new ArrayList<>();
        Imgproc.findContours(mFilteredMask, newContours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
        //Clear old list of contours
        mContours.clear();
        //Calculate the minimum number of pixels needed to qualify
        double minPixels = minContourArea * mRawImage.rows() * mRawImage.cols();
        // Filter contours by area
        Iterator<MatOfPoint> each = newContours.iterator();
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
    public static Point GetCentroid(Mat image)
    {
        Moments moments = Imgproc.moments(image, true);
        return new Point(
                moments.get_m10() / moments.get_m00(),
                moments.get_m01() / moments.get_m00()
        );
    }

    public Mat GetErosionKernel() {
        return  Imgproc.getStructuringElement(Imgproc.CV_SHAPE_ELLIPSE, new Size(2 * erodeKernelSize + 1, 2 * erodeKernelSize + 1));
    }
    public Mat GetDilationKernel() {
        return  Imgproc.getStructuringElement(Imgproc.CV_SHAPE_ELLIPSE, new Size(2 * dilateKernelSize + 1, 2 * dilateKernelSize + 1));
    }
}
