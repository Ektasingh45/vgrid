@echo off
REM ============================================================
REM  VGrid - double-click this file to compile and start the app.
REM  Needs: JDK 17+ installed (java and javac on PATH).
REM  JavaFX, the MySQL driver and Gson are in the lib folder, so no internet is needed.
REM  Database settings: db.properties (MySQL user/password, or data.mode=memory).
REM  For a second window (two users side by side) use open-another-window.bat.
REM  For the OCPP server (chargers report their own status) use run-ocpp-server.bat.
REM ============================================================
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
    echo ERROR: javac not found. Install JDK 17 or newer and add it to PATH.
    pause
    exit /b 1
)

echo Compiling VGrid...
if exist out rmdir /s /q out
mkdir out
dir /s /b src\main\java\*.java > out\sources.txt
javac -encoding UTF-8 -d out --module-path lib --add-modules javafx.controls -cp "lib\gson-2.11.0.jar;lib\Java-WebSocket-1.5.7.jar;lib\slf4j-api-2.0.13.jar" @out\sources.txt
if errorlevel 1 (
    echo.
    echo Compilation failed - see the errors above.
    pause
    exit /b 1
)
xcopy /e /i /q /y src\main\resources out >nul

echo Starting VGrid...
start "" javaw --module-path lib --add-modules javafx.controls -cp "out;lib\mysql-connector-j-8.4.0.jar;lib\gson-2.11.0.jar" com.vgrid.MainApp
