@echo off
REM ---------------------------------------------------------------------------
REM MiniCube - fabrication de l'executable et de l'installeur.
REM
REM Utilisation :
REM   - depuis PowerShell : .\package.bat
REM   - depuis cmd.exe    : package.bat
REM ---------------------------------------------------------------------------

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0package.ps1"
set EXITCODE=%ERRORLEVEL%

if not "%1"=="--no-pause" pause
exit /b %EXITCODE%
