@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 goto failed
java -jar dist\masr-delivery.jar %*
if errorlevel 1 goto failed
pause
exit /b 0
:failed
echo Could not run. Check that Java JDK 17 or newer is installed and on PATH.
pause
exit /b 1
