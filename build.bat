@echo off
REM ---------------------------------------------------------------------------
REM MiniCube - compilation (Windows)
REM
REM Ce fichier .bat sert de point d'entree : il n'est pas soumis a la strategie
REM d'execution PowerShell, contrairement aux fichiers .ps1. Il delegue le vrai
REM travail a build.ps1.
REM
REM Utilisation :
REM   - depuis PowerShell : .\build.bat
REM   - depuis cmd.exe    : build.bat
REM   - ou double-clic sur le fichier
REM ---------------------------------------------------------------------------

powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build.ps1"
set EXITCODE=%ERRORLEVEL%

if not "%1"=="--no-pause" pause
exit /b %EXITCODE%
