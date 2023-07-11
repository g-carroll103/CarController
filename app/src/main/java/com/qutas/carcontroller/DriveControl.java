package com.qutas.carcontroller;

import android.annotation.SuppressLint;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.util.Log;
import android.widget.TextView;

import com.hoho.android.usbserial.driver.UsbSerialDriver;
import com.hoho.android.usbserial.driver.UsbSerialPort;
import com.hoho.android.usbserial.driver.UsbSerialProber;

import java.nio.charset.StandardCharsets;
import java.util.List;

@SuppressLint("DefaultLocale")
public class DriveControl {
    UsbSerialPort port;
    TextView outText;

    enum State {
        STARTUP,
        CONNECTED,
        ERR,
        NO_UART
    }

    enum LedMode {
        LED_MODE_OFF,
        LED_MODE_AUTO,
        LED_MODE_AUDIO,
        LED_MODE_COMP
    }

    enum CarMode {
        CAR_MODE_STOPPED,
        CAR_MODE_STOPPED_TIMEOUT,
        CAR_MODE_MANUAL,
        CAR_MODE_AUTO
    }

    State appState = State.STARTUP;

    // servo control values - neutral (midpoint) and range from midpoint
    final int servoNeutral = 1000;
    final int steerScale = -300;
    final int throttleScale = 200;
    // autonomous or manual?
    boolean autonomousControl = false;
    // LED strip controls
    boolean enableLedStrip = true;
    // audio pulse for LEDs
    int audioPulseStrength = 0;
    boolean ledCompetitionMode = false;
    boolean enableAudioPulse = false;
    // steering/throttle requests and real value
    float throttleA = 0, steerA = 0;
    float throttleM = 0, steerM = 0;
    int throttleServoValue = 0, steerServoValue = 0;

    public DriveControl(TextView outText) {
        this.outText = outText;
    }

    public void SetSteerM(float str) {
        //Updates the steering keyboard input, preserving the prior throttle setting
        steerM = str;
    }

    public void SetThrottleM(float thr) {
        //Updates the throttle keyboard input, preserving the prior steer setting
        throttleM = thr;
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
        String strManual = String.format("M: %3.2f, %3.2f", throttleM, steerM);

        String controlMode = autonomousControl ? "A" : "M";
        String strOut = String.format("%s, %d, %d", controlMode, throttleServoValue, steerServoValue);

        return strAutonomous + "\n" + strManual + "\n" + strOut;
    }

    public String GetDriveCommand() {
        // determines new micro update string
        throttleServoValue = servoNeutral;
        steerServoValue = servoNeutral;
        //Check whether to use autonomous or manual controls:
        if (autonomousControl) {
            throttleServoValue += (int) (throttleScale * throttleA);
            steerServoValue += (int) (steerScale * steerA);
        } else {
            throttleServoValue += (int) (throttleScale * throttleM);
            steerServoValue += (int) (steerScale * steerM);
        }

        //CarMode carMode =
        CarMode carMode;
        if (appState != State.CONNECTED)
            carMode = CarMode.CAR_MODE_STOPPED;
        else if (!autonomousControl)
            carMode = CarMode.CAR_MODE_MANUAL;
        else
            carMode = CarMode.CAR_MODE_AUTO;

        LedMode ledMode;
        if (!enableLedStrip)
            ledMode = LedMode.LED_MODE_OFF;
        else if (ledCompetitionMode && autonomousControl)
            ledMode = LedMode.LED_MODE_COMP;
        else if (enableAudioPulse)
            ledMode = LedMode.LED_MODE_AUDIO;
        else
            ledMode = LedMode.LED_MODE_AUTO;

        // convert to format controller expects
        return String.format("T%04d S%04d M%1d L%1d P%03d\n",
                throttleServoValue, steerServoValue,
                carMode.ordinal(), ledMode.ordinal(), audioPulseStrength);
    }

    public void WriteDriveCommand() {
        //Updates the microcontroller servo positions
        try {
            //Prints the command with a timeout of 100ms:
            port.write(GetDriveCommand().getBytes(StandardCharsets.UTF_8), 100);
        } catch (Exception ignore) {
        }
    }

    public void SetAppState(State state) {
        //Update the app state and background colour
        appState = state;
        switch (appState) {
            case STARTUP -> {
                outText.setBackgroundColor(0x7F7F7F);
            }
            case CONNECTED -> {
                outText.setBackgroundColor(0xFF0000);
            }
            default -> {}
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
        if (port != null) {
            try {
                SetAppState(State.STARTUP);
                autonomousControl = false;
                SetControlsM(0, 0);
                WriteDriveCommand();
                port.close();
            } catch (Exception ignored) {
            }
        }
    }


}
