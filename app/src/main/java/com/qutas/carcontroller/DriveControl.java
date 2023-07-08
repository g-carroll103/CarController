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
        //Updates the steering keyboard input, preserving the prior throttle setting
         SetControlsM(this.throttleM, str);
    }

    public void SetThrottleM(float thr) {
        //Updates the throttle keyboard input, preserving the prior steer setting
         SetControlsM(thr, this.steerM);
    }

    public void SetControlsM(float thr, float str) {
        //Updates the steering keyboard input, preserving the prior throttle setting
        steerM = str;
        throttleM = thr;
    }

    public void SetControlsA(float thr, float str) {
        steerA = str;
        throttleA = thr;
    }

    public String GetPrintableControlString() {
        //Prints the current autonomous and manual controls, plus the actual control values
        String strAutonomous = String.format("A: %3.2f, %3.2f", throttleA, steerA);
        String strManual =     String.format("M: %3.2f, %3.2f", throttleM, steerM);

        String controlMode;
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

    public void WriteDriveCommand() {
        //Updates the microcontroller pwm values

        //Check whether to use autonomous or manual controls:
        if (autonomousControl){
            throttleMicros = servoMiddle + (int) (throttleScale * throttleA);
            steerMicros = servoMiddle + (int) (steerScale * steerA);
        }
        else {
            throttleMicros = servoMiddle + (int) (throttleScale * throttleM);
            steerMicros = servoMiddle + (int) (steerScale * steerM);
        }
        //Convert the values to plaintext to print
        //Changed to be compatible with James' control script
        String stringCommand = String.format("T%4d\tS%4d\n", throttleMicros, steerMicros);

        try {
                //Prints the command with a timeout of 100ms:
                port.write(stringCommand.getBytes(StandardCharsets.UTF_8), 100);
            } catch (Exception ignore) {
        }
    }

    public void SetAppState(State state){
        //Update the app state and background colour
        appState = state;
        switch (appState) {
            case STARTUP -> {
                outText.setBackgroundColor(0x7F7F7F);
                return;
            }
            case CONNECTED -> {
                outText.setBackgroundColor(0xFF0000);
                return;
            }
            case NO_UART -> outText.setBackgroundColor(0x7F7F7F);
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
            // Most devices have just one port (port 0)
            port = driver.getPorts().get(0);
            // Opens the port with commonly supported connections
            port.open(connection);
            port.setParameters(115200, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE);

        } catch (Exception e) {
            //If the connection fails:
            SetAppState(State.NO_UART);
            Log.w("Err initialising port: ", e.toString());
            return false;
        }
        //If no errors occurred, return a success message
        SetAppState(State.CONNECTED);
        return true;
    }

    public void ClosePort() {
        //Cleans up and closes the serial port output
        if (port != null){
            try {
                autonomousControl = false;
                SetControlsM(0, 0);
                WriteDriveCommand();
                port.close();
            } catch (Exception ignored) {
            }
        }
    }



}
