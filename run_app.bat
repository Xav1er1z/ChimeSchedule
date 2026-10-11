@echo off
setlocal
set SDK=C:\Users\Administrator\AppData\Local\Android\Sdk
set ADB=%SDK%\platform-tools\adb.exe
set EMU=%SDK%\emulator\emulator.exe
set APK=%~dp0app\build\outputs\apk\debug\app-debug.apk

echo ============================================
echo   Course Schedule - Run
echo ============================================
echo.

if not exist "%APK%" (
    echo [ERROR] APK not found. Please rebuild first.
    pause
    exit /b 1
)

"%ADB%" start-server >nul 2>&1

echo Checking for a running device...
"%ADB%" devices | findstr /r "device$" >nul
if not errorlevel 1 goto install

echo No device. Starting emulator Medium_Phone_API_37.0 ...
start "" "%EMU%" -avd Medium_Phone_API_37.0 -gpu swiftshader_indirect
echo Waiting for emulator to boot (1-3 minutes)...
"%ADB%" wait-for-device
:waitboot
timeout /t 3 /nobreak >nul
"%ADB%" shell getprop sys.boot_completed 2>nul | findstr "1" >nul
if errorlevel 1 goto waitboot

:install
echo Installing app...
"%ADB%" install -r "%APK%"
echo Launching app...
"%ADB%" shell am start -n com.example.courseschedule/.MainActivity
echo.
echo Done! The app should now be open on the emulator.
pause
endlocal