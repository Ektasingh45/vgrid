@echo off
REM ============================================================
REM  Starts the VGrid OCPP 1.6 server in this console window.
REM  Chargers (or a charger simulator) connect to
REM      ws://localhost:8887/ocpp/VGRID-PUNE-C1   (C1 ... C4)
REM  and their status (Available, Charging, Faulted ...) is saved in MySQL,
REM  so every open VGrid window shows it on the "VGrid OCPP Test Station".
REM  Uses the same db.properties as the app.
REM ============================================================
cd /d "%~dp0"
title VGrid OCPP server

where java >nul 2>nul
if errorlevel 1 goto nojava

if exist out\com\vgrid\ocpp\OcppServerMain.class goto run

echo Compiling VGrid (first time only)...
if not exist out mkdir out
dir /s /b src\main\java\*.java > out\sources.txt
javac -encoding UTF-8 -d out --module-path lib --add-modules javafx.controls -cp "lib\gson-2.11.0.jar;lib\Java-WebSocket-1.5.7.jar;lib\slf4j-api-2.0.13.jar" @out\sources.txt
if errorlevel 1 goto failed
xcopy /e /i /q /y src\main\resources out >nul

:run
java -Dfile.encoding=UTF-8 -cp "out;lib\mysql-connector-j-8.4.0.jar;lib\gson-2.11.0.jar;lib\Java-WebSocket-1.5.7.jar;lib\slf4j-api-2.0.13.jar;lib\slf4j-nop-2.0.13.jar" com.vgrid.ocpp.OcppServerMain
echo.
echo The OCPP server has stopped.
pause
exit /b 0

:nojava
echo ERROR: java / javac not found. Install JDK 17 or newer and add it to PATH.
pause
exit /b 1

:failed
echo.
echo Compilation failed - see the errors above.
pause
exit /b 1
