This application uses OpenCV to find the best available path for an autonomous car, and outputs the
appropriate throttle and steering controls over USB Serial to drive along a track.

Compile Instructions:
1. Download the OpenCV Android SDK and extract the OpenCV-android-sdk folder
2. Edit line 19 of settings.gradle to point to the 'OpenCV-android-sdk' path
3. Edit OpenCV-android-sdk\sdk\build.gradle and insert the following line under 'android {' on line 99:
    namespace "org.opencv"
4.