@echo off
title Stop Dual Virtual Phones
echo Stopping Teacher and Student virtual phones...
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -s emulator-5554 emu kill 2>nul
"%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe" -s emulator-5556 emu kill 2>nul
echo Both virtual phones have been stopped.
timeout /t 2 /nobreak >nul
