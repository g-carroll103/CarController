@echo off
"C:\\Users\\Guy\\Android\\Android SDK\\cmake\\3.22.1\\bin\\cmake.exe" ^
  "-HC:\\Users\\Guy\\AndroidStudioProjects\\CarController\\opencv\\libcxx_helper" ^
  "-DCMAKE_SYSTEM_NAME=Android" ^
  "-DCMAKE_EXPORT_COMPILE_COMMANDS=ON" ^
  "-DCMAKE_SYSTEM_VERSION=21" ^
  "-DANDROID_PLATFORM=android-21" ^
  "-DANDROID_ABI=x86" ^
  "-DCMAKE_ANDROID_ARCH_ABI=x86" ^
  "-DANDROID_NDK=C:\\Users\\Guy\\Android\\Android SDK\\ndk\\23.1.7779620" ^
  "-DCMAKE_ANDROID_NDK=C:\\Users\\Guy\\Android\\Android SDK\\ndk\\23.1.7779620" ^
  "-DCMAKE_TOOLCHAIN_FILE=C:\\Users\\Guy\\Android\\Android SDK\\ndk\\23.1.7779620\\build\\cmake\\android.toolchain.cmake" ^
  "-DCMAKE_MAKE_PROGRAM=C:\\Users\\Guy\\Android\\Android SDK\\cmake\\3.22.1\\bin\\ninja.exe" ^
  "-DCMAKE_LIBRARY_OUTPUT_DIRECTORY=C:\\Users\\Guy\\AndroidStudioProjects\\CarController\\opencv\\build\\intermediates\\cxx\\Debug\\7fo8673w\\obj\\x86" ^
  "-DCMAKE_RUNTIME_OUTPUT_DIRECTORY=C:\\Users\\Guy\\AndroidStudioProjects\\CarController\\opencv\\build\\intermediates\\cxx\\Debug\\7fo8673w\\obj\\x86" ^
  "-DCMAKE_BUILD_TYPE=Debug" ^
  "-BC:\\Users\\Guy\\AndroidStudioProjects\\CarController\\opencv\\.cxx\\Debug\\7fo8673w\\x86" ^
  -GNinja ^
  "-DANDROID_STL=c++_shared"
