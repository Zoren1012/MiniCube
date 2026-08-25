@echo off
REM ---------------------------------------------------------------------------
REM MiniCube - lancement (Windows)
REM
REM Utilisation :
REM   - depuis PowerShell : .\run.bat
REM   - depuis cmd.exe    : run.bat
REM   - ou double-clic sur le fichier
REM ---------------------------------------------------------------------------

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run.ps1"
set EXITCODE=%ERRORLEVEL%

if not "%EXITCODE%"=="0" (
    if not "%1"=="--no-pause" pause
)
exit /b %EXITCODE%
