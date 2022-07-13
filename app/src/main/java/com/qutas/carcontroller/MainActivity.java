package com.qutas.carcontroller;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraActivity;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.JavaCamera2View;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

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
        //Init the serial output timer
        Log.i("Controller/Startup","Configuring UI");

        //Views for displaying app output
        statusBox = findViewById(R.id.textState);
        servoBox = findViewById(R.id.textOutput);
        //
        imgJoystick = findViewById(R.id.imgJoystick);

        imgJoystick.setOnTouchListener(TouchListener());

        dc = new DriveControl(servoBox);
        dc.InitPort((UsbManager) getSystemService(Context.USB_SERVICE));

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
        Log.i("OpenCV", "Got camera frame!");
        return pf.onCameraFrame(inputFrame);
    }

    @Override
    public void onCameraViewStarted(int width, int height) {
        Log.i("Main", "onCameraViewStarted!!!");
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
    public View.OnTouchListener TouchListener(){
        return (v, event) -> {
            //Convert coords to relative inputs
            float outThrottle = -(2f * (event.getY() / imgJoystick.getHeight()) - 1);
            float outSteer =     (2f * (event.getX() / imgJoystick.getWidth())  - 1);

            //Limit values to +/- 1
            if (outSteer > 1) outSteer = 1;
            if (outThrottle > 1) outThrottle = 1;
            if (outSteer < -1) outSteer = -1;
            if (outThrottle < -1) outThrottle = -1;
            dc.SetControls(outThrottle, outSteer);
            String cmdText = String.format("Throttle: %3.2f, %d\nSteering: %3.2f, %d", outThrottle, dc.throttleMicros, outSteer, dc.steerMicros);
            servoBox.setText(cmdText);

            v.performClick();
            return true;
        };
    }

    public TimerTask TimerRoutine(){
        return new TimerTask() {
            @Override
            public void run() {
                // Write to serial port
                dc.WriteCommand(dc.throttle, dc.steer);
            }//end run
        }; //end new timertask
    } //end timerroutine


    //Old Code
/* Keyboard input control handling
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            //Steering controls
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                dc.SetSteer(0);
                return true;

            //Throttle controls
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottle(0);
                dc.appState = DriveControl.State.IDLE;
                return true;
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {

            //If a steering button was released
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_LEFT:
                dc.SetSteer(-1);
                return true;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                dc.SetSteer(1);
                return true;
            //Throttle controls
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:
                dc.SetThrottle(1);
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                dc.SetThrottle(-1);
            default:
                return super.onKeyUp(keyCode, event);
        }
    }
*/

}