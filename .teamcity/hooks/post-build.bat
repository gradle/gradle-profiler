@echo off
powershell.exe -NoProfile -NonInteractive -ExecutionPolicy Bypass -File "%~dp0maven-mirrors.ps1" -Phase post
exit /b %ERRORLEVEL%
