@echo off
setlocal
cd /d "%~dp0"
call build.bat
if errorlevel 1 goto failed
if not exist build\test-classes mkdir build\test-classes
java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -cp build\classes -d build\test-classes src\test\java\masr\delivery\PlatformTest.java
if errorlevel 1 goto failed
java -cp "build\classes;build\test-classes" masr.delivery.PlatformTest
if errorlevel 1 goto failed
pause
exit /b 0
:failed
echo Build or tests failed.
pause
exit /b 1
