@echo off
setlocal enabledelayedexpansion
title Course Schedule - Build and Run
echo ============================================================
echo    Course Schedule  -  one-click build & run
echo ============================================================
echo.

REM ============ 1. Find Android SDK ============
set "SDK="
if defined ANDROID_HOME set "SDK=%ANDROID_HOME%"
if not defined SDK if defined ANDROID_SDK_ROOT set "SDK=%ANDROID_SDK_ROOT%"
if not defined SDK if exist "%LOCALAPPDATA%\Android\Sdk" set "SDK=%LOCALAPPDATA%\Android\Sdk"
if not defined SDK (
    echo [ERROR] Android SDK not found.
    echo   Please open Android Studio once, or install the SDK,
    echo   then set ANDROID_HOME to the SDK folder and retry.
    pause
    exit /b 1
)
echo [OK] SDK  : %SDK%

REM ============ 2. Find JDK (Android Studio bundled JBR) ============
set "JAVA_HOME="
for %%D in ("C:\Program Files\Android\Android Studio\jbr" "C:\Program Files\Android\Android Studio1\jbr" "%LOCALAPPDATA%\Programs\Android Studio\jbr" "%ProgramFiles%\Android\Android Studio\jbr") do (
    if not defined JAVA_HOME if exist "%%~D\bin\java.exe" set "JAVA_HOME=%%~D"
)
if not defined JAVA_HOME (
    echo [WARN] Android Studio JDK not found in common locations.
    echo        Will try to use "java" from PATH.
) else (
    echo [OK] JDK  : %JAVA_HOME%
)

REM ============ 3. Build the APK ============
cd /d "%~dp0"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
echo.
echo [STEP 1/3] Building APK ... first time may take several minutes.
call gradlew.bat assembleDebug
if errorlevel 1 (
    echo.
    echo [ERROR] Build failed. See the messages above.
    echo         Copy the red error text and send it to me.
    pause
    exit /b 1
)
echo [OK] Build succeeded.
set "APK=%~dp0app\build\outputs\apk\debug\app-debug.apk"
if not exist "%APK%" (
    echo [ERROR] APK not found: %APK%
    pause
    exit /b 1
)

REM ============ 4. Locate adb ============
set "ADB=%SDK%\platform-tools\adb.exe"
if not exist "%ADB%" (
    echo [ERROR] adb not found. Install "Android SDK Platform-Tools" via SDK Manager.
    pause
    exit /b 1
)

REM ============ 5. Connect to a device ============
"%ADB%" start-server >nul 2>&1
echo.
echo [STEP 2/3] Looking for a device (phone or emulator)...
set "DEV="
for /f "skip=1 tokens=1" %%A in ('"%ADB%" devices') do set "DEV=%%A"
if defined DEV (
    echo [OK] Device found: %DEV%
    goto install
)

REM ============ 6. No device -> try to start an existing emulator ============
set "EMU=%SDK%\emulator\emulator.exe"
if not exist "%EMU%" (
    echo [ERROR] Android Emulator is not installed.
    echo   In Android Studio: Tools - Device Manager - Create Device.
    pause
    exit /b 1
)
set "AVD="
for /d %%A in ("%USERPROFILE%\.android\avd\*.avd") do if not defined AVD set "AVD=%%~nA"
if not defined AVD (
    echo.
    echo [INFO] No emulator (AVD) found yet.
    echo.
    echo   Please create one in Android Studio:
    echo     1. Open Android Studio
    echo     2. Menu: Tools ^(工具^) - Device Manager ^(设备管理器^)
    echo     3. Click "Create Device" ^(创建虚拟设备^)
    echo     4. Choose a "Pixel" phone - Next
    echo     5. Choose "API 36" image, click the download arrow, wait, then Next
    echo     6. Finish
    echo   Then double-click this script again.
    echo.
    pause
    exit /b 0
)

echo [OK] Found emulator: %AVD%
echo [STEP 3/3] Starting emulator (first boot may take 1-2 minutes)...
start "emulator" "%EMU%" -avd "%AVD%"

"%ADB%" wait-for-device
set /a tries=0
:waitboot
set /a tries+=1
if %tries% gtr 80 goto bootdone
"%ADB%" shell getprop sys.boot_completed 2>nul | findstr "1" >nul
if errorlevel 1 (
    timeout /t 3 /nobreak >nul
    goto waitboot
)
:bootdone
echo [OK] Emulator is ready.

:install
echo.
echo [STEP 3/3] Installing app...
"%ADB%" install -r "%APK%"
if errorlevel 1 (
    echo.
    echo [ERROR] Install failed. Try running this script again.
    pause
    exit /b 1
)
echo [OK] Installed. Launching app...
"%ADB%" shell am start -n com.example.courseschedule/.MainActivity

echo.
echo ============================================================
echo   Done! The app should now be open on your device/emulator.
echo ============================================================
pause
endlocal
