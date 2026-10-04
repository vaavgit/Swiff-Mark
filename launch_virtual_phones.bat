@echo off
title Launch Dual Virtual Phones (Teacher & Student)
echo ===================================================
echo   Launching Dual Lightweight Virtual Phones
echo   Teacher Phone : Port 5554 (emulator-5554)
echo   Student Phone : Port 5556 (emulator-5556)
echo ===================================================

echo Starting Teacher Phone...
start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd TeacherPhone -port 5554 -gpu auto

timeout /t 3 /nobreak >nul

echo Starting Student Phone...
start "" "%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe" -avd StudentPhone -port 5556 -gpu auto

echo Both virtual phone windows are now appearing on your desktop!
echo You can place them side-by-side.
timeout /t 4 /nobreak >nul
