package com.qutas.carcontroller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.CameraActivity;
import org.opencv.android.JavaCamera2View;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.engine.OpenCVEngineInterface;
import org.opencv.osgi.OpenCVNativeLoader;


import java.util.Collections;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends CameraActivity implements CvCameraViewListener2{

    PathFinder pf;
    DriveControl dc;
    TextView statusBox;
    TextView servoBox;
    ImageView imgJoystick;
    JavaCamera2View camPreview;
    TimerTask tt = TimerRoutine();
    Timer tmr;

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
        statusBox = findViewById(R.id.textState);
        servoBox = findViewById(R.id.textOutput);
        //
        imgJoystick = findViewById(R.id.imgJoystick);
        imgJoystick.setOnTouchListener(TouchPadListener());

        dc = new DriveControl(servoBox);
        dc.InitPort((UsbManager) getSystemService(Context.USB_SERVICE));

        //Make sure timer is configured - crashes if re-scheduling existing task
        if (tmr == null) {
            tmr = new Timer();
            tmr.scheduleAtFixedRate(tt, 250, 50);
        }
        camPreview.enableView();
    }

    @Override
    protected void onDestroy() {
        dc.ClosePort();
        super.onDestroy();
    }

    @Override
    public void onPause()
    {
        tt.cancel();
        super.onPause();
        if (camPreview != null)
            camPreview.disableView();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        dc.SetControlsA(0.3f, (float)(pf.targetSteer) );
        return pf.onCameraFrame(inputFrame);
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

    //Dummy steering controls
    public View.OnTouchListener TouchPadListener(){
        return (v, event) -> {
            //Convert coords to relative inputs
            float outThrottle = -(2f * (event.getY() / imgJoystick.getHeight()) - 1);
            float outSteer =     (2f * (event.getX() / imgJoystick.getWidth())  - 1);

            //Limit values to +/- 1
            if (outSteer > 1) outSteer = 1;
            if (outThrottle > 1) outThrottle = 1;
            if (outSteer < -1) outSteer = -1;
            if (outThrottle < -1) outThrottle = -1;
            dc.SetControlsM(outThrottle, outSteer);

            v.performClick();
            return true;
        };
    }

    public TimerTask TimerRoutine(){
        return new TimerTask() {
            @Override
            public void run() {
                // Write to serial port
                dc.WriteCommand();
            }//end run
        }; //end new timertask
    } //end timerroutine
    //Old Code
    // Keyboard input control handling
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            //Steering controls
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                dc.SetSteerM(0);
                servoBox.setText(dc.GetControlString());
                return true;

            //Throttle controls
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottleM(0);
                servoBox.setText(dc.GetControlString());
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {

            //If a steering button was pressed
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                dc.SetSteerM(-1);
                servoBox.setText(dc.GetControlString());
                return true;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                dc.SetSteerM(1);
                servoBox.setText(dc.GetControlString());
                return true;
            //Throttle controls
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:
                dc.SetThrottleM(1);
                servoBox.setText(dc.GetControlString());
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottleM(-1);
                servoBox.setText(dc.GetControlString());
                return true;
            case KeyEvent.KEYCODE_M:
                dc.autonomousControl = false;
                return true;
            case KeyEvent.KEYCODE_I:
                dc.autonomousControl = true;
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }



}