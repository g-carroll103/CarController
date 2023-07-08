package com.qutas.carcontroller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.widget.Switch;
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

public class MainActivity extends CameraActivity implements CvCameraViewListener2 {

    //Class-wide object declarations
    PathFinder pf;
    DriveControl dc;
    TextView infoBox;
    JavaCamera2View camPreview;
    TimerTask tt = TimerRoutine();
    Timer tmr;
    boolean colorChecking = false;

    // UI Function defi nitions:
    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        // Returns a list of available cameras
        return Collections.singletonList(camPreview);
    }

    //////////////////////////
    //UI Event Handlers:
    @Override
    protected void onCreate(Bundle savedInstance) {
        //Runs when the UI window is created:
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_main);
        camPreview = findViewById(R.id.javaCamera2View);
        pf = new PathFinder(camPreview, this);
        camPreview.setCvCameraViewListener(this);
        camPreview.setMaxFrameSize(640, 480);

        Switch modeToggle = findViewById(R.id.switch1);
        modeToggle.setOnCheckedChangeListener((buttonView, isChecked) -> colorChecking = isChecked);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onResume() {
        //When the app (re)starts running in the foreground:

        //Call the parent resume function:
        super.onResume();

        //Get handle of textbox element:
        infoBox = findViewById(R.id.textOutput);
        //Create Drive Controller, pass handle of textbox:
        dc = new DriveControl(infoBox);
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
    public void onCameraViewStarted(int width, int height) {
        if (null == pf) {
            Log.i("onCameraViewStarted error", "Pathfinder Object is null somehow");
        }
        pf.onCameraViewStarted(width, height);
    }

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
                infoBox.setText(dc.GetPrintableControlString());
                return true;

            //Throttle controls
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottleM(0);
                infoBox.setText(dc.GetPrintableControlString());
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
                infoBox.setText(dc.GetPrintableControlString());
                return true;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                dc.SetSteerM(1);
                infoBox.setText(dc.GetPrintableControlString());
                return true;

            //Throttle controls
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:
                dc.SetThrottleM(1);
                infoBox.setText(dc.GetPrintableControlString());
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottleM(-1);
                infoBox.setText(dc.GetPrintableControlString());
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

    @Override
    public void onPause() {
        tt.cancel();
        super.onPause();
        if (camPreview != null)
            camPreview.disableView();
    }

    @Override
    protected void onDestroy() {
        dc.ClosePort();
        super.onDestroy();
    }

    @Override
    public void onCameraViewStopped() {
        pf.onCameraViewStopped();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        if (colorChecking) {
            // Call colour detection/analysis func
            Mat imgOutput = pf.GetColourValue(inputFrame.rgba());
            //Show the marked up image values
            return imgOutput;
        } else {
            //Run Pathfinder function, display annotated image
            dc.SetControlsA(0.27f, (float) (pf.targetSteer));

            return pf.FindPath(inputFrame);
        }
    }

    public TimerTask TimerRoutine() {
        //This timer repeatedly calls the drive controller update function
        return new TimerTask() {
            @Override
            public void run() {
                // Write current command to serial port
                dc.WriteDriveCommand();
            }//end run
        }; //end new timertask
    } //end timerroutine

}