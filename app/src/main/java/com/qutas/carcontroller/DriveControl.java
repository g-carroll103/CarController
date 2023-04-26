package com.qutas.carcontroller;

import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Timer;
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
        CONNECTED,
        ERR,
        NO_UART
    }

    State appState = State.STARTUP;

    final int servoMiddle = 1500;
    final int steerScale = -300;
    final int throttleScale = 200;
    boolean autonomousControl = false;
    float throttleA = 0, steerA = 0;
    float throttleM = 0, steerM = 0;
    int throttleMicros = 0, steerMicros = 0;
    // Servo midpoint in micros

    public DriveControl(TextView outText){
        this.outText = outText;
    }

    public void SetSteerM(float str) {
         SetControlsM(this.throttleM, str);
    }

    public void SetThrottleM(float thr) {
         SetControlsM(thr, this.steerM);
    }

    public void SetControlsM(float thr, float str) {
        steerM = str;
        throttleM = thr;
    }

    public void SetControlsA(float thr, float str) {
        steerA = str;
        throttleA = thr;
    }

    public String GetControlString() {

        String strAutonomous = String.format("A: %3.2f, %3.2f", throttleA, steerA);
        String strManual =     String.format("M: %3.2f, %3.2f", throttleM, steerM);

        String controlMode = "";
        if (autonomousControl){
            controlMode = "A";
        }
        else
        {
            controlMode = "M";
        }
        String strOut = String.format("%s, %d, %d", controlMode, throttleMicros, steerMicros);

        return strAutonomous + "\n" + strManual + "\n" + strOut;
    }

    public void WriteCommand() {
        //Convert float inputs to servo microseconds out
        if (autonomousControl){
            throttleMicros = servoMiddle + (int) (throttleScale * throttleA);
            steerMicros = servoMiddle + (int) (steerScale * steerA);
        }
        else {
            throttleMicros = servoMiddle + (int) (throttleScale * throttleM);
            steerMicros = servoMiddle + (int) (steerScale * steerM);
        }
        String stringCommand = String.format("%4d,%4d\n", throttleMicros, steerMicros);
        try {
                port.write(stringCommand.getBytes(StandardCharsets.UTF_8), 100);
            } catch (Exception ignore) {
        }
    }

    public void SetAppState(State state){
        appState = state;
        switch (appState) {
            case STARTUP:
                outText.setBackgroundColor(0x7F7F7F);
                return;
            case CONNECTED:
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
            SetAppState(State.NO_UART);
            Log.w("Err initialising port: ", e.toString());
            return false;
        }
        SetAppState(State.CONNECTED);
        return true;
    }

    public void ClosePort() {
        if (port != null){
            try {
                autonomousControl = false;
                SetControlsM(0, 0);
                WriteCommand();
                port.close();
            } catch (Exception ignored) {
            }
        }
    }



}
