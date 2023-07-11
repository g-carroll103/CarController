package com.qutas.carcontroller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.Switch;
import android.widget.TextView;

import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.android.CameraActivity;
import org.opencv.android.JavaCamera2View;
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

    // UI Function definitions:
    @Override
    protected List<? extends CameraBridgeViewBase> getCameraViewList() {
        // Returns a list of available camera displays
        return Collections.singletonList(camPreview);
    }

    //////////////////////////
    //UI Event Handlers:
    @Override
    protected void onCreate(Bundle savedInstance) {
        Log.i("MainActivity","onCreate");
        //Runs when the UI window is created:
        super.onCreate(savedInstance);

        // keep the screen on - NO SLEEP! will drain battery if app not closed
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        setContentView(R.layout.activity_main);
        //Get reference to camera preview widget
        Log.i("Main.onCreate","assigning camPreview to javaCVCameraView");
        camPreview = findViewById(R.id.javaCamera2View);
        camPreview.setMaxFrameSize(1300, 1000);
        camPreview.setCvCameraViewListener(this);

         //Pass widget to pathfinder class
        pf = new PathFinder(camPreview, this);
        //Get handles to UI widgets:
        infoBox = findViewById(R.id.textOutput);

        // set up switches
        ((Switch)findViewById(R.id.debugSwitch))
                .setOnCheckedChangeListener((buttonView, isChecked) -> colorChecking = isChecked);
        ((Switch)findViewById(R.id.ledOnSwitch))
                .setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if(dc != null)
                        dc.enableLedStrip = isChecked;
                });
        ((Switch)findViewById(R.id.competionLedSwitch))
                .setOnCheckedChangeListener((buttonView, isChecked) -> {
                    if(dc != null)
                        dc.ledCompetitionMode = isChecked;
                });

        //Create Drive Controller, pass handle of textbox:
        dc = new DriveControl(infoBox);
        //Connect to the USB device and output commands
        dc.InitPort((UsbManager) getSystemService(Context.USB_SERVICE));
    }

    void DumpCameraProperties(){

        CameraManager manager = (CameraManager) this.getBaseContext().getSystemService(Context.CAMERA_SERVICE);
        String[] camList;
        try {
            camList = manager.getCameraIdList();
            for (String cameraId : camList) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                Log.w("Camera ID:", cameraId);
                List keyList = characteristics.getKeys();
                for (int i = 0; i < keyList.size(); i++){

                    CameraCharacteristics.Key key = (CameraCharacteristics.Key)keyList.get(i);
                    Log.w("Camera Property:", characteristics.get(key).toString() );
                }
                Log.w("==========","============");
            }
        } catch (CameraAccessException e) {
            throw new RuntimeException(e);
        }
    }

    // visibility change signals from Android
    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onResume() {
        //When the app (re)starts running in the foreground:

        //Call the parent resume function:
        super.onResume();

        //Make sure timer is configured - crashes if re-scheduling existing task
        if (tmr == null) {
            tmr = new Timer();
            tmr.scheduleAtFixedRate(tt, 250, 50);
        }
        //Restart camera preview
        Log.i("Main", "onResume starting camera");
        camPreview.enableView();

    }

    @Override
    public void onCameraViewStarted(int width, int height) {
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
                return true;

            //Throttle controls
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottleM(0);
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
                return true;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                dc.SetSteerM(1);
                return true;

            //Throttle controls
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:
                dc.SetThrottleM(1);
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottleM(-1);
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
        //infoBox.setText(dc.GetPrintableControlString());

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
        Log.i("Main", "onDestroy() called");
    }

    @Override
    public void onCameraViewStopped() {
        pf.onCameraViewStopped();
    }

    // callback from cameraview object
    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame frameIn) {

        Mat rgbImage = frameIn.rgba();

        //OpenCV insists on displaying the preview image in landscape:
        //The image is rotated to portrait for processing then rotated back
        //The app is locked in landscape and used rotated 90 deg
        //Somehow this is just easier than rotating the image according to OpenCV

        Core.rotate(rgbImage, rgbImage, Core.ROTATE_90_CLOCKWISE);
        if (colorChecking) {
            // Call colour detection/analysis func
            rgbImage = pf.GetColourValue(rgbImage);
        } else {
            //Run Pathfinder function, display annotated image
            rgbImage = pf.FindPath(rgbImage);
            if(dc != null)
                dc.SetControlsA((float)pf.throttleOutput, (float)pf.steeringOutput);
        }
        Core.rotate(rgbImage, rgbImage, Core.ROTATE_90_COUNTERCLOCKWISE);
        //Show the annotated image
        return rgbImage;
    }
    public TimerTask TimerRoutine() {
        //This timer repeatedly calls the drive controller update function
        return new TimerTask() {
            @Override
            public void run() {
                // Write current command to serial port
                dc.WriteDriveCommand();
                infoBox.setText(dc.GetDriveCommand());
            }//end run
        }; //end new timertask
    } //end timerroutine
}