@echo off
setlocal
cd /d "%~dp0"
if not exist build\classes mkdir build\classes
if not exist dist mkdir dist
if exist build\sources.txt del build\sources.txt
for /r src\main\java %%F in (*.java) do echo "%%F">> build\sources.txt
java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -d build\classes @build\sources.txt
if errorlevel 1 exit /b 1
copy /y src\main\resources\* build\classes\ >nul
if errorlevel 1 exit /b 1
java -m jdk.jartool/sun.tools.jar.Main --create --file dist\masr-delivery.jar --main-class masr.delivery.Main -C build\classes .
if errorlevel 1 exit /b 1
echo Built dist\masr-delivery.jar
