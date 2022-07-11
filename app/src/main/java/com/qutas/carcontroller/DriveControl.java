package com.qutas.carcontroller;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

public class DriveControl {
    UsbSerialPort port;
    Timer tmr;
    TextView outText;
    enum State {
        STARTUP,
        IDLE,
        RUNNING,
        ESTOP,
        ERR,
        NO_UART
    }

    State appState = State.STARTUP;
    
    float throttle = 0, steer = 0;
    // Servo midpoint in micros
    final int servoMiddle = 1500;
    // Servo output scale in micros
    final int steerScale = 400;
    final int throttleScale = 200;

    public DriveControl(TextView outText){
        this.outText = outText;
        tmr = new Timer();
        TimerTask tt = TimerRoutine();
        tmr.scheduleAtFixedRate(tt, 250, 250);
    }

    public void SetControls(float str, float thr) {
        steer = str;
        throttle = thr;

        String readableOutput = String.format("%3.2f, %3.2f", thr, str);
        Log.i("Set Control", readableOutput);
        try {
            outText.setText(readableOutput);
        }
            catch (Exception ignore){
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

    public void WriteCommand(float thr, float str) {
        //Convert float inputs to servo microseconds out
        int thrOut = servoMiddle + (int) (throttleScale * thr);
        int strOut = servoMiddle + (int) (steerScale * str);

        String strCommand = String.format("%4d,%4d", thrOut, strOut);

        if ((null != port) && port.isOpen()) {
            try {
                port.write(strCommand.getBytes(StandardCharsets.UTF_8), 100);
            } catch (Exception e) {
                Log.e("UART out err", e.toString());
            } //end try port write

        }
    }

    public void SetAppState(State state){
        appState = state;

        switch (appState) {
            case IDLE:
                outText.setBackgroundColor(0x7F7F7F);
                return;
            case RUNNING:
                outText.setBackgroundColor(0x7FFF7F);
                return;
            case ESTOP:
                outText.setBackgroundColor(0xFF7F00);
                return;
            case ERR:
                outText.setBackgroundColor(0xFF0000);
                return;
            case NO_UART:
                outText.setBackgroundColor(0x7F7F7F);
        }
    }

    public boolean InitPort(UsbManager manager) {
        // Find all available drivers from attached devices.

        List<UsbSerialDriver> availableDrivers = UsbSerialProber.getDefaultProber().findAllDrivers(manager);
        if (availableDrivers.isEmpty()) {
            SetAppState(State.NO_UART);
            Log.w("InitPort", "No serial ports found");
            return false;
        }

        // Open a connection to the first available driver.
        UsbSerialDriver driver = availableDrivers.get(0);
        UsbDeviceConnection connection = manager.openDevice(driver.getDevice());

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

    public void ClosePort() {
        if (port != null){
                try {
                WriteCommand(0, 0);
                port.close();
            } catch (Exception ignored) {
            }
        }
    }

}
