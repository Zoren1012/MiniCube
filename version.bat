@echo off
REM ---------------------------------------------------------------------------
REM MiniCube - preparation d'une nouvelle version.
REM
REM Utilisation :  .\version.bat 1.4.0
REM ---------------------------------------------------------------------------

if "%~1"=="" (
    echo Utilisation : version.bat ^<numero^>
    echo Exemple     : version.bat 1.4.0
    pause
    exit /b 1
)

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0version.ps1" %1 %2 %3
set EXITCODE=%ERRORLEVEL%
pause
exit /b %EXITCODE%
