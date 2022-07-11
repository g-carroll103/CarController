package com.qutas.carcontroller;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
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
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Mat;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;

import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.SurfaceView;
import android.view.WindowManager;
import com.qutas.carcontroller.PathFinder;
import org.opencv.android.CameraActivity;

public class MainActivity extends CameraActivity implements CvCameraViewListener2{

    PathFinder pf;
    DriveControl dc;
    TextView statusBox;
    TextView servoBox;
    ImageView imgBox;
    CameraBridgeViewBase camPreview;

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_main);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onResume() {
        super.onResume();
        //Init the serial output timer
        Log.d("Controller/Startup","Configuring UI");

        statusBox = findViewById(R.id.textState);
        servoBox = findViewById(R.id.textOutput);
        imgBox = findViewById(R.id.imageView);

        if (null != imgBox) {
            imgBox.setMaxHeight(imgBox.getWidth() * 2 / 3);
            imgBox.setOnTouchListener(TouchListener());
        }

        // error handling when serial port is not found
        //Log.d("Controller/Startup", "Creating external class objects");
        dc = new DriveControl(servoBox);
        dc.InitPort((UsbManager) getSystemService(Context.USB_SERVICE));
    }

    @Override
    protected void onDestroy() {
        dc.ClosePort();
        super.onDestroy();
    }

    //Dummy steering controls
    public View.OnTouchListener TouchListener(){
        return (v, event) -> {
            //Convert coords to relative inputs
            float outSteer =     (2f * (event.getX() / imgBox.getWidth())  - 1);
            float outThrottle = -(2f * (event.getY() / imgBox.getHeight()) - 1);
            //Limit values to +/- 1
            if (outSteer > 1) outSteer = 1;
            if (outThrottle > 1) outThrottle = 1;
            if (outSteer < -1) outSteer = -1;
            if (outThrottle < -1) outThrottle = -1;

            dc.SetControls(outThrottle, outSteer);
            v.performClick();
            return true;
        };
    }

    @Override
    public void onPause()
    {
        super.onPause();
        if (camPreview != null)
            camPreview.disableView();
    }

    public void onCameraViewStarted(int width, int height) {
    }

    public void onCameraViewStopped() {
    }

    public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
        return inputFrame.rgba();
    }

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