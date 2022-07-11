package com.qutas.carcontroller;
import android.util.Log;

import org.opencv.android.OpenCVLoader;

public class PathFinder {

    public PathFinder(){
        if (OpenCVLoader.initDebug()){
            Log.d("myTag", "OpenCV loaded");
        }
    } //end constructor

    public float[] GetDriveCommands(){
        return new float[]{0, 0};
    }
}
