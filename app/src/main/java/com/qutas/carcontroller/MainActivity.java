package com.qutas.carcontroller;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import java.nio.charset.StandardCharsets;
import java.util.Timer;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.util.List;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    UsbSerialPort port;
    float throttle = 0, steer = 0;

    // Servo midpoint in micros
    final int servoMiddle = 1500;
    // Servo output scale in micros
    final int steerScale = 400;
    final int throttleScale = 200;

    Timer tmr;
    TextView statusBox;
    TextView servoBox;
    ImageView imgBox;
    private PendingIntent permissionIntent;

    enum State {
        STARTUP,
        IDLE,
        RUNNING,
        ESTOP,
        ERR,
        NO_UART
    }

    State appState = State.STARTUP;

    @Override
    protected void onCreate(Bundle savedInstance) {
        super.onCreate(savedInstance);
        setContentView(R.layout.activity_main);
    }

    @Override
    protected void onResume() {
        super.onResume();
        //Init the serial output timer
        tmr = new Timer();
        TimerTask tt = TimerRoutine();
        tmr.scheduleAtFixedRate(tt, 250, 250);
        statusBox = findViewById(R.id.textState);
        servoBox = findViewById(R.id.textOutput);
        imgBox = findViewById(R.id.imageView);
        imgBox.setMaxHeight(imgBox.getWidth() * 2 / 3);

        imgBox.setOnTouchListener(TouchListener() );
        // error handling when serial port is not found
        InitPort();
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            //Steering controls
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_A:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_D:
                SetSteer(0);
                return true;

            //Throttle controls
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                SetThrottle(0);
                appState = State.IDLE;
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
                SetSteer(-1);
                return true;
            case KeyEvent.KEYCODE_D:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                SetSteer(1);
                return true;
            //Throttle controls
            case KeyEvent.KEYCODE_W:
            case KeyEvent.KEYCODE_DPAD_UP:
                SetThrottle(1);
                return true;
            case KeyEvent.KEYCODE_S:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                SetThrottle(-1);
            default:
                return super.onKeyUp(keyCode, event);
        }
    }

    @Override
    protected void onDestroy() {
        if (appState != State.NO_UART && port != null && port.isOpen()) {
            try {
                WriteCommand(0, 0);
                port.close();
            } catch (Exception ignored) {
            }
        }
        super.onDestroy();
    }

    // Non-Android event declarations
    public boolean InitPort() {
        // Find all available drivers from attached devices.
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            SetAppState(State.NO_UART);
            Log.w("InitPort", "No serial ports found");
            return false;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());
        manager.requestPermission(driver.getDevice(), permissionIntent);

        if (connection == null) {
            Log.e("InitPort", "Connection object is null");
            return false;
        }

        // Set up port for UART driver
        try {
            port = driver.getPorts().get(0); // Most devices have just one port (port 0)
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);
        } catch (Exception e) {
            SetAppState(State.ERR);
            Log.w("Err initialising port: ", e.toString());
            return false;
        }
        SetAppState(State.IDLE);
        return true;
    }

    public View.OnTouchListener TouchListener(){
        return (v, event) -> {
            float outSteer =     (2f * (event.getX() / imgBox.getWidth())  - 1);
            float outThrottle = -(2f * (event.getY() / imgBox.getHeight()) - 1);

            if (outSteer > 1) outSteer = 1;
            if (outThrottle > 1) outThrottle = 1;
            if (outSteer < -1) outSteer = -1;
            if (outThrottle < -1) outThrottle = -1;

            SetControls(outThrottle, outSteer);
            v.performClick();
            return true;
        };
    }

    public void SetThrottle(float thr) {
        SetControls(thr, steer);
    }

    public void SetSteer(float str) {
        SetControls(throttle, str);
    }

    public void SetControls(float str, float thr) {
        steer = str;
        throttle = thr;

        String readableOutput = String.format("%3.2f, %3.2f", thr, str);
        Log.i("Set Control", readableOutput);
        servoBox.setText(readableOutput);
    }

    public void WriteCommand(float thr, float str) {
        //Convert float inputs to servo microseconds out
        int thrOut = servoMiddle + (int) (throttleScale * thr);
        int strOut = servoMiddle + (int) (steerScale * str);

        String strCommand = String.format("%4d,%4d", thrOut, strOut);
        //Log.i("Serial Command", strCommand);
        try {
            servoBox.setText(strCommand);
        } catch (Exception e) {
            // N/A
        }
        if ((null != port) && port.isOpen()) {
            try {
                port.write(strCommand.getBytes(StandardCharsets.UTF_8), 100);
            } catch (Exception e) {
                Log.e("UART out err", e.toString());
            } //end try port write

        }
    }

    public void SetAppState(MainActivity.State state){
        appState = state;
        if (statusBox != null) statusBox.setText(appState.toString());

        switch (appState) {
            case IDLE:
                statusBox.setBackgroundColor(0x7F7F7F);
                return;
            case RUNNING:
                statusBox.setBackgroundColor(0x7FFF7F);
                return;
            case ESTOP:
                statusBox.setBackgroundColor(0xFF7F00);
                return;
            case ERR:
                statusBox.setBackgroundColor(0xFF0000);
                return;
            case NO_UART:
                statusBox.setBackgroundColor(0x7F7F7F);
        }
    }


    public TimerTask TimerRoutine(){
        return new TimerTask() {
            @Override
            public void run() {
                //Convert set inputs to byte array
                // Write to serial port
                if (appState != State.ERR)
                {
                    WriteCommand(throttle, steer);
                }//endif
            }//end run
        }; //end new timertask
    } //end timerroutine
}