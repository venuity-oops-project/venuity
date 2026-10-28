@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0java\run.ps1" %*
if %ERRORLEVEL% neq 0 (
    echo.
    echo Venuity execution exited with code %ERRORLEVEL%.
    pause
)
endlocal
