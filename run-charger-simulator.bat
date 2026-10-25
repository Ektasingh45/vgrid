@echo off
REM ============================================================
REM  Opens the VGrid charger simulator: four pretend chargers (C1 ... C4 of the
REM  "VGrid OCPP Test Station") that talk OCPP 1.6 to the VGrid OCPP server.
REM  Start run-ocpp-server.bat first, then press "Connect all" in the simulator.
REM ============================================================
cd /d "%~dp0"

where java >nul 2>nul
if errorlevel 1 goto nojava

if exist out\com\vgrid\simulator\ChargerSimulatorApp.class goto run

echo Compiling VGrid (first time only)...
if not exist out mkdir out
dir /s /b src\main\java\*.java > out\sources.txt
javac -encoding UTF-8 -d out --module-path lib --add-modules javafx.controls -cp "lib\gson-2.11.0.jar;lib\Java-WebSocket-1.5.7.jar;lib\slf4j-api-2.0.13.jar" @out\sources.txt
if errorlevel 1 goto failed
xcopy /e /i /q /y src\main\resources out >nul

:run
start "" javaw --module-path lib --add-modules javafx.controls -cp "out;lib\gson-2.11.0.jar;lib\Java-WebSocket-1.5.7.jar;lib\slf4j-api-2.0.13.jar;lib\slf4j-nop-2.0.13.jar" com.vgrid.simulator.ChargerSimulatorApp
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
