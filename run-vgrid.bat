@echo off
REM ============================================================
REM  VGrid - double-click this file to compile and start the app.
REM  Needs: JDK 17+ installed (java and javac on PATH).
REM  JavaFX jars are in the lib folder, so no internet is needed.
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
javac -encoding UTF-8 -d out --module-path lib --add-modules javafx.controls @out\sources.txt
if errorlevel 1 (
    echo.
    echo Compilation failed - see the errors above.
    pause
    exit /b 1
)
xcopy /e /i /q /y src\main\resources out >nul

echo Starting VGrid...
start "" javaw --module-path lib --add-modules javafx.controls -cp out com.vgrid.MainApp
