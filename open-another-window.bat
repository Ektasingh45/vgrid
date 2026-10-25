@echo off
REM ============================================================
REM  Opens one more VGrid window WITHOUT recompiling, so you can sign in
REM  as a second user next to the first window (both use the same MySQL database).
REM  Run run-vgrid.bat once first - it compiles the app into the "out" folder.
REM ============================================================
cd /d "%~dp0"

if not exist out\com\vgrid\MainApp.class (
    echo The app is not compiled yet. Double-click run-vgrid.bat first.
    pause
    exit /b 1
)

start "" javaw --module-path lib --add-modules javafx.controls -cp "out;lib\mysql-connector-j-8.4.0.jar;lib\gson-2.11.0.jar" com.vgrid.MainApp
