package com.qutas.carcontroller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.TextView;

import org.opencv.android.CameraActivity;
import org.opencv.android.JavaCamera2View;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;


import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends CameraActivity implements CvCameraViewListener2{

    PathFinder pf;
    DriveControl dc;
    TextView servoBox;
    JavaCamera2View camPreview;
    TimerTask tt = TimerRoutine();
    Timer tmr;
    boolean colorChecking = false;

    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        return Collections.singletonList(camPreview);
    }

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_main);
        camPreview = findViewById(R.id.javaCamera2View);
        pf = new PathFinder(camPreview, this);
        camPreview.setCvCameraViewListener(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onResume() {
        super.onResume();

        //Views for displaying app output
        servoBox = findViewById(R.id.textOutput);
        //
        dc = new DriveControl(servoBox);
        //Connect to the USB device and output commands
        dc.InitPort((UsbManager) getSystemService(Context.USB_SERVICE));

        //Make sure timer is configured - crashes if re-scheduling existing task
        if (tmr == null) {
            tmr = new Timer();
            tmr.scheduleAtFixedRate(tt, 250, 50);
        }
        //Restart camera preview
        camPreview.enableView();
    }

    @Override
    protected void onDestroy() {
        dc.ClosePort();
        super.onDestroy();
    }

    @Override
    public void onPause() {
        tt.cancel();
        super.onPause();
        if (camPreview != null)
            camPreview.disableView();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        dc.SetControlsA(0.27f, (float)(pf.targetSteer) );
        if (colorChecking) {
            Mat rgbImage = inputFrame.rgba();

            /*
            Scalar black = new Scalar(0,0,0);
            Scalar white = new Scalar(255,255,255);
            Scalar blue = new Scalar(50,50,255);

            int sampleX = rgbImage.cols() / 2;
            int sampleY = rgbImage.rows() / 2;
            Mat rgbSample = rgbImage.submat(sampleX, sampleX+1, sampleY, sampleY+1);
            double[] rgbValue = rgbSample.get(0,0);

            Mat hsvSample = new Mat();
            Imgproc.cvtColor(rgbSample, hsvSample, Imgproc.COLOR_RGB2HSV_FULL);
            double[] hsvValue = hsvSample.get(0,0);

            Imgproc.circle(rgbImage,
                new Point(sampleX, sampleY),
                4,
                black, 2
            );

            Imgproc.rectangle(rgbImage, new Rect(0, rgbImage.rows() - 60, rgbImage.cols(),90),blue,-1);

            Imgproc.putText(
                rgbImage,
                String.format("H: %3.0f  S: %3.0f  V: %3.0f", hsvValue[0],hsvValue[1],hsvValue[2]),
                new Point(sampleX-200, rgbImage.rows() - 35),
                0,
                0.9,
                black
            );
            Imgproc.putText(
                rgbImage,
                String.format("R: %3.0f  G: %3.0f  B: %3.0f", rgbValue[0],rgbValue[1],rgbValue[2]),
                new Point(sampleX-200, rgbImage.rows()- 10),
                0,
                0.9,
                black
            );
            */
            return rgbImage;

        } else {
            return pf.onCameraFrame(inputFrame);
        }
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        if (null == pf){
            Log.i("onCameraViewStarted error", "pf is null somehow");
        }
        pf.onCameraViewStarted(width, height);
    }

    @Override
    public void onCameraViewStopped(){
        pf.onCameraViewStopped();
    }

    public TimerTask TimerRoutine(){
        //This timer repeatedly calls the drive controller update function
        return new TimerTask() {
            @Override
            public void run() {
                // Write current command to serial port
                dc.WriteCommand();
            }//end run
        }; //end new timertask
    } //end timerroutine

    // Keyboard input control handling

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        //Whenever a key input is released, reset that control to 0:
        switch (keyCode) {
            //Steering controls
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                dc.SetSteerM(0);
                servoBox.setText(dc.GetPrintableControlString());
                return true;

            //Throttle controls
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottleM(0);
                servoBox.setText(dc.GetPrintableControlString());
                return true;

            //If the keycode isn't recognised:
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        //Handle keydown commands
        switch (keyCode) {

            //If a steering button was pressed
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                dc.SetSteerM(-1);
                servoBox.setText(dc.GetPrintableControlString());
                return true;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                dc.SetSteerM(1);
                servoBox.setText(dc.GetPrintableControlString());
                return true;

            //Throttle controls
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:
                dc.SetThrottleM(1);
                servoBox.setText(dc.GetPrintableControlString());
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottleM(-1);
                servoBox.setText(dc.GetPrintableControlString());
                return true;

            //Automation controls:
            case KeyEvent.KEYCODE_M:
                dc.autonomousControl = false;
                return true;
            case KeyEvent.KEYCODE_I:
                dc.autonomousControl = true;
                return true;

            //If the keycode isn't recognised:
            default:
                return super.onKeyUp(keyCode, event);
        }
    }



}